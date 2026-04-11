package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the GATT server side of BLE communication.
 * Each device runs a GATT server that accepts connections from peers.
 * Remote devices write messages to MESSAGE_WRITE_CHAR, and we send messages
 * back via notifications on MESSAGE_NOTIFY_CHAR.
 */
class GattServerManager(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val protocol: MessageProtocol
) {

    companion object {
        private const val TAG = "GattServerManager"
    }

    private var gattServer: BluetoothGattServer? = null
    private val subscribedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val deviceMtus = ConcurrentHashMap<String, Int>()

    data class IncomingMessage(
        val senderAddress: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IncomingMessage) return false
            return senderAddress == other.senderAddress && data.contentEquals(other.data)
        }
        override fun hashCode(): Int = 31 * senderAddress.hashCode() + data.contentHashCode()
    }

    data class ConnectionEvent(
        val deviceAddress: String,
        val connected: Boolean
    )

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var serviceAddedDeferred: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            Log.d(TAG, "onServiceAdded: uuid=${service?.uuid}, success=$success, status=$status")
            serviceAddedDeferred?.complete(success)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            try {
                val address = device.address
                Log.d(TAG, "Connection state changed: $address -> $newState (status: $status)")

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionEvents.tryEmit(ConnectionEvent(address, true))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        subscribedDevices.remove(address)
                        deviceMtus.remove(address)
                        _connectionEvents.tryEmit(ConnectionEvent(address, false))
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onConnectionStateChange", e)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            try {
                Log.d(TAG, "MTU changed for ${device.address}: $mtu")
                deviceMtus[device.address] = mtu
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onMtuChanged", e)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            try {
                when (characteristic.uuid) {
                    BluetoothConstants.MESSAGE_WRITE_CHAR_UUID -> {
                        // Try to reassemble chunks
                        val assembled = protocol.reassemble(value)
                        if (assembled != null) {
                            _incomingMessages.tryEmit(IncomingMessage(device.address, assembled))
                        }

                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                    }
                    else -> {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onCharacteristicWriteRequest", e)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            try {
                when (characteristic.uuid) {
                    BluetoothConstants.PROFILE_CHAR_UUID -> {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0,
                            characteristic.value ?: byteArrayOf()
                        )
                    }
                    else -> {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onCharacteristicReadRequest", e)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            try {
                if (descriptor.uuid == BluetoothConstants.CCCD_UUID) {
                    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        Log.d(TAG, "Device ${device.address} subscribed to notifications")
                        subscribedDevices[device.address] = device
                    } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        Log.d(TAG, "Device ${device.address} unsubscribed from notifications")
                        subscribedDevices.remove(device.address)
                    }
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onDescriptorWriteRequest", e)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            try {
                Log.v(TAG, "Notification sent to ${device.address}, status: $status")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in onNotificationSent", e)
            }
        }
    }

    suspend fun start(): Boolean {
        if (_isRunning.value) return true

        try {
            gattServer = bluetoothManager.openGattServer(context, gattCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server")
                return false
            }

            val service = createService()
            serviceAddedDeferred = CompletableDeferred()
            gattServer?.addService(service)

            // Wait for onServiceAdded callback — service is not queryable until this fires
            val added = withTimeoutOrNull(5_000L) {
                serviceAddedDeferred?.await()
            } ?: false

            if (!added) {
                Log.e(TAG, "GATT service registration timed out or failed")
                gattServer?.close()
                gattServer = null
                return false
            }

            _isRunning.value = true
            Log.d(TAG, "GATT server started — service registered successfully")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth connect permission", e)
            return false
        }
    }

    fun stop() {
        try {
            gattServer?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception closing GATT server", e)
        }
        gattServer = null
        subscribedDevices.clear()
        deviceMtus.clear()
        _isRunning.value = false
        Log.d(TAG, "GATT server stopped")
    }

    fun sendNotification(deviceAddress: String, data: ByteArray): Boolean {
        val device = subscribedDevices[deviceAddress]
        if (device == null) { Log.w(TAG, "sendNotification: device $deviceAddress not subscribed"); return false }
        val server = gattServer
        if (server == null) { Log.w(TAG, "sendNotification: GATT server is null"); return false }
        val service = server.getService(BluetoothConstants.SERVICE_UUID)
        if (service == null) { Log.w(TAG, "sendNotification: service not found"); return false }
        val characteristic = service.getCharacteristic(BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID)
        if (characteristic == null) { Log.w(TAG, "sendNotification: characteristic not found"); return false }

        val mtu = deviceMtus[deviceAddress] ?: BluetoothConstants.DEFAULT_MTU
        val chunks = protocol.chunk(data, mtu - 3) // ATT overhead

        return try {
            for (chunk in chunks) {
                characteristic.value = chunk
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception sending notification", e)
            false
        }
    }

    fun updateProfileCharacteristic(displayName: String) {
        val server = gattServer ?: return
        val service = server.getService(BluetoothConstants.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(BluetoothConstants.PROFILE_CHAR_UUID) ?: return
        characteristic.value = displayName.toByteArray(Charsets.UTF_8)
    }

    fun isDeviceSubscribed(address: String): Boolean = subscribedDevices.containsKey(address)

    fun getConnectedDeviceAddresses(): Set<String> = subscribedDevices.keys.toSet()

    private fun createService(): BluetoothGattService {
        val service = BluetoothGattService(
            BluetoothConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Message write characteristic (client writes messages to us)
        val writeCharacteristic = BluetoothGattCharacteristic(
            BluetoothConstants.MESSAGE_WRITE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Message notify characteristic (we send messages to subscribed clients)
        val notifyCharacteristic = BluetoothGattCharacteristic(
            BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // CCCD for enabling notifications
        val cccd = BluetoothGattDescriptor(
            BluetoothConstants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        notifyCharacteristic.addDescriptor(cccd)

        // Profile characteristic (readable user info)
        val profileCharacteristic = BluetoothGattCharacteristic(
            BluetoothConstants.PROFILE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)
        service.addCharacteristic(profileCharacteristic)

        return service
    }
}
