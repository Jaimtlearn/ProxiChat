package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.proxichat.app.domain.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages outgoing GATT client connections to remote devices.
 * Connects to remote GATT servers, discovers services, subscribes to notifications,
 * and writes messages to the remote device's write characteristic.
 */
class GattClientManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val protocol: MessageProtocol
) {

    companion object {
        private const val TAG = "GattClientManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectionMtus = ConcurrentHashMap<String, Int>()
    private val reconnectJobs = ConcurrentHashMap<String, Job>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private val autoReconnectAddresses = ConcurrentHashMap.newKeySet<String>()

    data class IncomingNotification(
        val senderAddress: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IncomingNotification) return false
            return senderAddress == other.senderAddress && data.contentEquals(other.data)
        }
        override fun hashCode(): Int = 31 * senderAddress.hashCode() + data.contentHashCode()
    }

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _incomingNotifications = MutableSharedFlow<IncomingNotification>(extraBufferCapacity = 64)
    val incomingNotifications: SharedFlow<IncomingNotification> = _incomingNotifications.asSharedFlow()

    private var serviceDiscoveryDeferred: CompletableDeferred<Boolean>? = null
    private var mtuDeferred: CompletableDeferred<Int>? = null

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                val address = gatt.device.address
                Log.d(TAG, "Connection state changed: $address -> $newState (status: $status)")

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        updateState(address, ConnectionState.CONNECTING)
                        reconnectAttempts[address] = 0
                        // Discover services to proceed with setup
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connections.remove(address)
                        connectionMtus.remove(address)
                        updateState(address, ConnectionState.DISCONNECTED)
                        gatt.close()

                        // Auto-reconnect if enabled
                        if (autoReconnectAddresses.contains(address)) {
                            scheduleReconnect(address)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in connection state change", e)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                val address = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered for $address")

                    // Request higher MTU
                    gatt.requestMtu(BluetoothConstants.PREFERRED_MTU)
                } else {
                    Log.e(TAG, "Service discovery failed for $address with status $status")
                    serviceDiscoveryDeferred?.complete(false)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in services discovered", e)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            try {
                val address = gatt.device.address
                Log.d(TAG, "MTU changed for $address: $mtu")
                connectionMtus[address] = mtu

                // Now subscribe to notifications
                subscribeToNotifications(gatt)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in MTU changed", e)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            try {
                val address = gatt.device.address
                if (descriptor.uuid == BluetoothConstants.CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Subscribed to notifications on $address")
                    connections[address] = gatt
                    updateState(address, ConnectionState.CONNECTED)
                    serviceDiscoveryDeferred?.complete(true)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in descriptor write", e)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            try {
                if (characteristic.uuid == BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID) {
                    val data = characteristic.value ?: return
                    val assembled = protocol.reassemble(data)
                    if (assembled != null) {
                        _incomingNotifications.tryEmit(
                            IncomingNotification(gatt.device.address, assembled)
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in characteristic changed", e)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            try {
                if (characteristic.uuid == BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID) {
                    val assembled = protocol.reassemble(value)
                    if (assembled != null) {
                        _incomingNotifications.tryEmit(
                            IncomingNotification(gatt.device.address, assembled)
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in characteristic changed", e)
            }
        }
    }

    suspend fun connect(address: String, autoReconnect: Boolean = true): Boolean {
        if (connections.containsKey(address)) {
            Log.d(TAG, "Already connected to $address")
            return true
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid device address: $address", e)
            return false
        }

        updateState(address, ConnectionState.CONNECTING)
        if (autoReconnect) {
            autoReconnectAddresses.add(address)
        }

        serviceDiscoveryDeferred = CompletableDeferred()

        try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }

            if (gatt == null) {
                Log.e(TAG, "Failed to start GATT connection to $address")
                updateState(address, ConnectionState.FAILED)
                return false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth connect permission", e)
            updateState(address, ConnectionState.FAILED)
            return false
        }

        // Wait for connection setup to complete
        val result = withTimeoutOrNull(BluetoothConstants.CONNECTION_TIMEOUT_MS) {
            serviceDiscoveryDeferred?.await()
        } ?: false

        if (!result) {
            updateState(address, ConnectionState.FAILED)
        }

        return result
    }

    fun disconnect(address: String) {
        autoReconnectAddresses.remove(address)
        reconnectJobs[address]?.cancel()
        reconnectJobs.remove(address)
        reconnectAttempts.remove(address)

        val gatt = connections.remove(address)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception disconnecting", e)
        }
        connectionMtus.remove(address)
        updateState(address, ConnectionState.DISCONNECTED)
    }

    fun disconnectAll() {
        autoReconnectAddresses.clear()
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        reconnectAttempts.clear()

        connections.forEach { (address, gatt) ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception disconnecting $address", e)
            }
        }
        connections.clear()
        connectionMtus.clear()
        _connectionStates.value = emptyMap()
    }

    fun sendMessage(address: String, data: ByteArray): Boolean {
        val gatt = connections[address] ?: return false
        val service = gatt.getService(BluetoothConstants.SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(BluetoothConstants.MESSAGE_WRITE_CHAR_UUID) ?: return false

        val mtu = connectionMtus[address] ?: BluetoothConstants.DEFAULT_MTU
        val chunks = protocol.chunk(data, mtu - 3)

        return try {
            for (chunk in chunks) {
                characteristic.value = chunk
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(characteristic)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception writing characteristic", e)
            false
        }
    }

    fun isConnected(address: String): Boolean = connections.containsKey(address)

    fun getConnectionState(address: String): ConnectionState {
        return _connectionStates.value[address] ?: ConnectionState.DISCONNECTED
    }

    private fun subscribeToNotifications(gatt: BluetoothGatt) {
        try {
            val service = gatt.getService(BluetoothConstants.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "ProxiChat service not found on remote device")
                serviceDiscoveryDeferred?.complete(false)
                return
            }

            val notifyChar = service.getCharacteristic(BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID)
            if (notifyChar == null) {
                Log.e(TAG, "Notify characteristic not found")
                serviceDiscoveryDeferred?.complete(false)
                return
            }

            gatt.setCharacteristicNotification(notifyChar, true)

            val descriptor = notifyChar.getDescriptor(BluetoothConstants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                // Some devices work without explicit CCCD write
                connections[gatt.device.address] = gatt
                updateState(gatt.device.address, ConnectionState.CONNECTED)
                serviceDiscoveryDeferred?.complete(true)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception subscribing to notifications", e)
            serviceDiscoveryDeferred?.complete(false)
        }
    }

    private fun updateState(address: String, state: ConnectionState) {
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            put(address, state)
        }
    }

    private fun scheduleReconnect(address: String) {
        val attempts = reconnectAttempts.getOrDefault(address, 0)
        if (attempts >= BluetoothConstants.MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Max reconnect attempts reached for $address")
            autoReconnectAddresses.remove(address)
            updateState(address, ConnectionState.FAILED)
            return
        }

        reconnectJobs[address]?.cancel()
        reconnectJobs[address] = scope.launch {
            val delayMs = BluetoothConstants.RECONNECT_DELAY_MS * (attempts + 1) // Exponential-ish backoff
            Log.d(TAG, "Scheduling reconnect to $address in ${delayMs}ms (attempt ${attempts + 1})")
            delay(delayMs)
            reconnectAttempts[address] = attempts + 1
            connect(address, autoReconnect = true)
        }
    }
}
