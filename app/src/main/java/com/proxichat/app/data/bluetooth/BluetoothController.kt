package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Central coordinator for all Bluetooth operations.
 * Manages the lifecycle of BLE scanning, advertising, GATT server, and GATT client.
 * Provides a unified API for the rest of the app.
 */
class BluetoothController(
    private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    companion object {
        private const val TAG = "BluetoothController"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    val protocol = MessageProtocol()

    val advertiser: BleAdvertiser? = bluetoothAdapter?.let { BleAdvertiser(it) }
    val scanner: BleScanner? = bluetoothAdapter?.let { BleScanner(it) }
    val gattServer: GattServerManager = GattServerManager(context, bluetoothManager, protocol)
    val gattClient: GattClientManager? = bluetoothAdapter?.let { GattClientManager(context, it, protocol) }

    // --- Public state flows ---

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    val isScanning: StateFlow<Boolean>
        get() = scanner?.isScanning ?: MutableStateFlow(false)

    val discoveredDevices: StateFlow<Map<String, ChatDevice>>
        get() = scanner?.discoveredDevices ?: MutableStateFlow(emptyMap())

    private val _typingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingStates: StateFlow<Map<String, Boolean>> = _typingStates.asStateFlow()

    data class ReceivedMessage(
        val senderAddress: String,
        val message: MessageProtocol.ProtocolMessage
    )

    private val _receivedMessages = MutableSharedFlow<ReceivedMessage>(extraBufferCapacity = 64)
    val receivedMessages: SharedFlow<ReceivedMessage> = _receivedMessages.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<Pair<String, ConnectionState>>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<Pair<String, ConnectionState>> = _connectionEvents.asSharedFlow()

    private var displayName: String = "User"

    // Track server-side and client-side connections independently.
    // A device is CONNECTED if either side is connected.
    // This prevents a client FAILED from overwriting a working server connection.
    private val serverConnectionStates = ConcurrentHashMap<String, ConnectionState>()
    private val clientConnectionStates = ConcurrentHashMap<String, ConnectionState>()

    suspend fun initialize(displayName: String) {
        this.displayName = displayName

        // Start GATT server and wait for service registration to complete
        val serverStarted = gattServer.start()
        if (!serverStarted) {
            Log.e(TAG, "GATT server failed to start — connections may not work")
        }
        gattServer.updateProfileCharacteristic(displayName)

        // Listen for incoming messages from GATT server (devices writing to us)
        scope.launch {
            gattServer.incomingMessages.collect { incoming ->
                Log.d(TAG, "GATT server received data from ${incoming.senderAddress} (${incoming.data.size} bytes)")
                handleIncomingData(incoming.senderAddress, incoming.data)
            }
        }

        // Listen for incoming notifications from GATT client (devices sending notifications)
        scope.launch {
            gattClient?.incomingNotifications?.collect { notification ->
                Log.d(TAG, "GATT client received notification from ${notification.senderAddress} (${notification.data.size} bytes)")
                handleIncomingData(notification.senderAddress, notification.data)
            }
        }

        // Forward GATT server connection events — track separately from client
        scope.launch {
            gattServer.connectionEvents.collect { event ->
                val address = event.deviceAddress
                val state = if (event.connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
                Log.d(TAG, "Server connection event: $address → $state")
                serverConnectionStates[address] = state
                val effective = effectiveConnectionState(address)
                scanner?.updateDeviceConnectionState(address, effective)
                _connectionEvents.emit(address to effective)
            }
        }

        // Forward GATT client connection states — track separately from server
        scope.launch {
            gattClient?.connectionStates?.collect { states ->
                states.forEach { (address, state) ->
                    Log.d(TAG, "Client connection state: $address → $state")
                    clientConnectionStates[address] = state
                    val effective = effectiveConnectionState(address)
                    scanner?.updateDeviceConnectionState(address, effective)
                    _connectionEvents.emit(address to effective)
                }
            }
        }

        Log.d(TAG, "BluetoothController initialized with name: $displayName")
    }

    /**
     * Compute the effective connection state for a device.
     * CONNECTED if either server or client path is connected.
     * This prevents a client-side FAILED from overwriting a working server-side connection
     * (e.g., when iOS connects to us but our outgoing connection to iOS fails).
     */
    private fun effectiveConnectionState(address: String): ConnectionState {
        val server = serverConnectionStates[address] ?: ConnectionState.DISCONNECTED
        val client = clientConnectionStates[address] ?: ConnectionState.DISCONNECTED

        return when {
            server == ConnectionState.CONNECTED || client == ConnectionState.CONNECTED -> ConnectionState.CONNECTED
            server == ConnectionState.CONNECTING || client == ConnectionState.CONNECTING -> ConnectionState.CONNECTING
            client == ConnectionState.FAILED && server == ConnectionState.DISCONNECTED -> ConnectionState.FAILED
            else -> ConnectionState.DISCONNECTED
        }
    }

    fun startDiscovery() {
        advertiser?.startAdvertising(displayName)
        scanner?.startScanning()
    }

    fun stopDiscovery() {
        scanner?.stopScanning()
    }

    suspend fun connectToDevice(address: String): Boolean {
        // If already connected via server path (remote device connected to us), reuse that
        if (serverConnectionStates[address] == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connected to $address via server path — skipping client connect")
            scanner?.updateDeviceConnectionState(address, ConnectionState.CONNECTED)
            sendProfile(address)
            return true
        }

        scanner?.updateDeviceConnectionState(address, ConnectionState.CONNECTING)
        val result = gattClient?.connect(address) ?: false
        if (result) {
            sendProfile(address)
        } else {
            // Client failed, but check if server path is connected
            val effective = effectiveConnectionState(address)
            scanner?.updateDeviceConnectionState(address, effective)
            if (effective == ConnectionState.CONNECTED) {
                Log.d(TAG, "Client connect to $address failed, but server path is active")
                sendProfile(address)
                return true
            }
        }
        return result
    }

    fun disconnectFromDevice(address: String) {
        gattClient?.disconnect(address)
        clientConnectionStates.remove(address)
        serverConnectionStates.remove(address)
        scanner?.updateDeviceConnectionState(address, ConnectionState.DISCONNECTED)
    }

    fun disconnectAll() {
        gattClient?.disconnectAll()
    }

    fun sendTextMessage(address: String, text: String, messageId: String): Boolean {
        val localAddress = bluetoothAdapter?.address ?: "local"
        val protoMessage = protocol.createTextMessage(text, localAddress)
        val data = protocol.serialize(protoMessage.copy(id = messageId))
        return sendData(address, data)
    }

    fun sendAck(address: String, messageId: String, status: String) {
        val localAddress = bluetoothAdapter?.address ?: "local"
        val ackMessage = protocol.createAckMessage(messageId, status, localAddress)
        val data = protocol.serialize(ackMessage)
        sendData(address, data)
    }

    fun sendTypingIndicator(address: String, isTyping: Boolean) {
        val localAddress = bluetoothAdapter?.address ?: "local"
        val typingMessage = protocol.createTypingMessage(isTyping, localAddress)
        val data = protocol.serialize(typingMessage)
        sendData(address, data)
    }

    private fun sendProfile(address: String) {
        val localAddress = bluetoothAdapter?.address ?: "local"
        val profileMessage = protocol.createProfileMessage(displayName, 0, localAddress)
        val data = protocol.serialize(profileMessage)
        sendData(address, data)
    }

    private fun sendData(address: String, data: ByteArray): Boolean {
        // Try client path first (we connected to them)
        if (gattClient?.isConnected(address) == true) {
            return gattClient.sendMessage(address, data)
        }
        // Try server path (they connected to us)
        if (gattServer.isDeviceSubscribed(address)) {
            return gattServer.sendNotification(address, data)
        }
        Log.w(TAG, "No connection found for $address")
        return false
    }

    private fun handleIncomingData(senderAddress: String, data: ByteArray) {
        val message = protocol.deserialize(data)
        if (message == null) {
            Log.e(TAG, "Failed to deserialize ${data.size} bytes from $senderAddress: ${String(data, Charsets.UTF_8).take(200)}")
            return
        }
        Log.d(TAG, "Received message type=${message.type} from $senderAddress")

        when (message.type) {
            "MSG" -> {
                val emitted = _receivedMessages.tryEmit(ReceivedMessage(senderAddress, message))
                if (!emitted) {
                    Log.e(TAG, "Failed to emit received message — buffer full")
                }
                sendAck(senderAddress, message.id, "DELIVERED")
            }
            "ACK" -> {
                _receivedMessages.tryEmit(ReceivedMessage(senderAddress, message))
            }
            "TYPING" -> {
                val isTyping = message.payload["isTyping"] as? Boolean ?: false
                _typingStates.value = _typingStates.value.toMutableMap().apply {
                    put(senderAddress, isTyping)
                }
            }
            "PROFILE" -> {
                val name = message.payload["displayName"] as? String ?: return
                scanner?.updateDeviceDisplayName(senderAddress, name)
            }
            "DISCONNECT" -> {
                disconnectFromDevice(senderAddress)
            }
        }
    }

    fun updateDisplayName(name: String) {
        displayName = name
        advertiser?.updateDisplayName(name)
        gattServer.updateProfileCharacteristic(name)
    }

    fun isDeviceConnected(address: String): Boolean {
        return gattClient?.isConnected(address) == true || gattServer.isDeviceSubscribed(address)
    }

    fun getConnectionState(address: String): ConnectionState {
        return effectiveConnectionState(address)
    }

    fun shutdown() {
        scanner?.stopScanning()
        advertiser?.stopAdvertising()
        gattClient?.disconnectAll()
        gattServer.stop()
        serverConnectionStates.clear()
        clientConnectionStates.clear()
        protocol.clearReassemblyBuffers()
    }
}
