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
        private const val SERVICE_DISCOVERY_DELAY_MS = 600L
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectionMtus = ConcurrentHashMap<String, Int>()
    private val reconnectJobs = ConcurrentHashMap<String, Job>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private val autoReconnectAddresses = ConcurrentHashMap.newKeySet<String>()

    // Per-connection GATT references so we can clean up on timeout
    private val pendingGatts = ConcurrentHashMap<String, BluetoothGatt>()
    // Per-connection deferreds — no more shared state across connections
    private val setupDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

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

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                val address = gatt.device.address
                Log.d(TAG, "onConnectionStateChange: $address status=$status newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.e(TAG, "Connection failed for $address with GATT status $status (0x${status.toString(16)})")
                    // Remove from pendingGatts without calling disconnect/close — we close via gatt param below
                    pendingGatts.remove(address)
                    setupDeferreds.remove(address)?.complete(false)
                    connections.remove(address)
                    connectionMtus.remove(address)
                    updateState(address, ConnectionState.FAILED)
                    gatt.close()
                    if (autoReconnectAddresses.contains(address)) {
                        scheduleReconnect(address)
                    }
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        updateState(address, ConnectionState.CONNECTING)
                        reconnectAttempts[address] = 0
                        refreshGattCache(gatt)
                        // Delay before service discovery — many Android BLE stacks need this
                        scope.launch {
                            delay(SERVICE_DISCOVERY_DELAY_MS)
                            try {
                                val started = gatt.discoverServices()
                                if (!started) {
                                    Log.e(TAG, "discoverServices() returned false for $address")
                                    setupDeferreds.remove(address)?.complete(false)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception calling discoverServices for $address", e)
                                setupDeferreds.remove(address)?.complete(false)
                            }
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        // Remove from pendingGatts without calling disconnect — already disconnected
                        pendingGatts.remove(address)
                        setupDeferreds.remove(address)?.complete(false)
                        connections.remove(address)
                        connectionMtus.remove(address)
                        updateState(address, ConnectionState.DISCONNECTED)
                        gatt.close()

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
                    val services = gatt.services
                    Log.d(TAG, "Services discovered for $address: ${services.map { it.uuid }}")

                    val hasOurService = gatt.getService(BluetoothConstants.SERVICE_UUID) != null
                    if (!hasOurService) {
                        Log.e(TAG, "ProxiChat service NOT found on $address after discovery")
                        setupDeferreds.remove(address)?.complete(false)
                        return
                    }

                    // Request higher MTU
                    gatt.requestMtu(BluetoothConstants.PREFERRED_MTU)
                } else {
                    Log.e(TAG, "Service discovery failed for $address with status $status")
                    setupDeferreds.remove(address)?.complete(false)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in services discovered", e)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            try {
                val address = gatt.device.address
                Log.d(TAG, "MTU changed for $address: $mtu (status: $status)")
                connectionMtus[address] = mtu

                // Proceed even if MTU request was rejected — use whatever MTU we got
                subscribeToNotifications(gatt)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in MTU changed", e)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            try {
                val address = gatt.device.address
                if (descriptor.uuid == BluetoothConstants.CCCD_UUID) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Subscribed to notifications on $address")
                        pendingGatts.remove(address)
                        connections[address] = gatt
                        updateState(address, ConnectionState.CONNECTED)
                        setupDeferreds.remove(address)?.complete(true)
                    } else {
                        Log.e(TAG, "CCCD write failed for $address with status $status")
                        setupDeferreds.remove(address)?.complete(false)
                    }
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

        // Cancel any pending connection to the same device
        cleanupPendingGatt(address)
        setupDeferreds.remove(address)?.complete(false)

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

        val deferred = CompletableDeferred<Boolean>()
        setupDeferreds[address] = deferred

        val gatt: BluetoothGatt?
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }

            if (gatt == null) {
                Log.e(TAG, "Failed to start GATT connection to $address")
                setupDeferreds.remove(address)
                updateState(address, ConnectionState.FAILED)
                return false
            }

            // Store reference so we can clean up on timeout
            pendingGatts[address] = gatt
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth connect permission", e)
            setupDeferreds.remove(address)
            updateState(address, ConnectionState.FAILED)
            return false
        }

        // Wait for full connection setup (connect → discover → MTU → subscribe)
        val result = withTimeoutOrNull(BluetoothConstants.CONNECTION_TIMEOUT_MS) {
            deferred.await()
        } ?: false

        if (!result) {
            Log.w(TAG, "Connection to $address timed out or failed — cleaning up GATT")
            // Clean up the leaked GATT connection
            cleanupPendingGatt(address)
            setupDeferreds.remove(address)
            updateState(address, ConnectionState.FAILED)
        }

        return result
    }

    fun disconnect(address: String) {
        autoReconnectAddresses.remove(address)
        reconnectJobs[address]?.cancel()
        reconnectJobs.remove(address)
        reconnectAttempts.remove(address)
        setupDeferreds.remove(address)?.complete(false)
        cleanupPendingGatt(address)

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

        // Complete all pending setup deferreds
        setupDeferreds.forEach { (_, deferred) -> deferred.complete(false) }
        setupDeferreds.clear()

        // Clean up pending (not yet connected) GATTs
        pendingGatts.forEach { (address, gatt) ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception cleaning up pending GATT $address", e)
            }
        }
        pendingGatts.clear()

        // Clean up established connections
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
        val address = gatt.device.address
        try {
            val service = gatt.getService(BluetoothConstants.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "ProxiChat service not found on remote device $address")
                setupDeferreds.remove(address)?.complete(false)
                return
            }

            val notifyChar = service.getCharacteristic(BluetoothConstants.MESSAGE_NOTIFY_CHAR_UUID)
            if (notifyChar == null) {
                Log.e(TAG, "Notify characteristic not found on $address")
                setupDeferreds.remove(address)?.complete(false)
                return
            }

            gatt.setCharacteristicNotification(notifyChar, true)

            val descriptor = notifyChar.getDescriptor(BluetoothConstants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val written = gatt.writeDescriptor(descriptor)
                if (!written) {
                    Log.e(TAG, "writeDescriptor returned false for $address")
                    setupDeferreds.remove(address)?.complete(false)
                }
            } else {
                // Some devices work without explicit CCCD write
                pendingGatts.remove(address)
                connections[address] = gatt
                updateState(address, ConnectionState.CONNECTED)
                setupDeferreds.remove(address)?.complete(true)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception subscribing to notifications", e)
            setupDeferreds.remove(address)?.complete(false)
        }
    }

    /**
     * Clear Android's GATT service cache via hidden API.
     * Without this, Android returns stale cached services if the remote device
     * restarted or changed its GATT database since the last connection.
     */
    private fun refreshGattCache(gatt: BluetoothGatt): Boolean {
        return try {
            val method = gatt.javaClass.getMethod("refresh")
            val result = method.invoke(gatt) as? Boolean ?: false
            Log.d(TAG, "GATT cache refresh: $result")
            result
        } catch (e: Exception) {
            Log.w(TAG, "GATT cache refresh not available: ${e.message}")
            false
        }
    }

    /**
     * Close and remove a pending (not yet fully connected) GATT reference.
     * Prevents Android GATT resource exhaustion after failed connection attempts.
     */
    private fun cleanupPendingGatt(address: String) {
        val gatt = pendingGatts.remove(address) ?: return
        try {
            gatt.disconnect()
            gatt.close()
            Log.d(TAG, "Cleaned up pending GATT for $address")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception cleaning up pending GATT", e)
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
            val delayMs = BluetoothConstants.RECONNECT_DELAY_MS * (attempts + 1)
            Log.d(TAG, "Scheduling reconnect to $address in ${delayMs}ms (attempt ${attempts + 1})")
            delay(delayMs)
            reconnectAttempts[address] = attempts + 1
            connect(address, autoReconnect = true)
        }
    }
}
