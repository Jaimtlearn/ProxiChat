package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ChatMessage
import com.proxichat.app.domain.model.ConnectionState
import com.proxichat.app.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
                handleIncomingData(incoming.senderAddress, incoming.data)
            }
        }

        // Listen for incoming notifications from GATT client (devices sending notifications)
        scope.launch {
            gattClient?.incomingNotifications?.collect { notification ->
                handleIncomingData(notification.senderAddress, notification.data)
            }
        }

        // Forward GATT server connection events
        scope.launch {
            gattServer.connectionEvents.collect { event ->
                val state = if (event.connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
                scanner?.updateDeviceConnectionState(event.deviceAddress, state)
                _connectionEvents.emit(event.deviceAddress to state)
            }
        }

        // Forward GATT client connection states
        scope.launch {
            gattClient?.connectionStates?.collect { states ->
                states.forEach { (address, state) ->
                    scanner?.updateDeviceConnectionState(address, state)
                    _connectionEvents.emit(address to state)
                }
            }
        }

        Log.d(TAG, "BluetoothController initialized with name: $displayName")
    }

    fun startDiscovery() {
        advertiser?.startAdvertising(displayName)
        scanner?.startScanning()
    }

    fun stopDiscovery() {
        scanner?.stopScanning()
    }

    suspend fun connectToDevice(address: String): Boolean {
        scanner?.updateDeviceConnectionState(address, ConnectionState.CONNECTING)
        val result = gattClient?.connect(address) ?: false
        if (result) {
            // Send profile info after connecting
            sendProfile(address)
        }
        return result
    }

    fun disconnectFromDevice(address: String) {
        gattClient?.disconnect(address)
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
        val message = protocol.deserialize(data) ?: return

        when (message.type) {
            "MSG" -> {
                _receivedMessages.tryEmit(ReceivedMessage(senderAddress, message))
                // Auto-send delivery ACK
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
                val device = scanner?.discoveredDevices?.value?.get(senderAddress) ?: return
                scanner?.discoveredDevices?.value?.toMutableMap()?.apply {
                    put(senderAddress, device.copy(displayName = name))
                }?.let { /* update state handled through scanner */ }
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

    fun getConnectionState(address: String): ConnectionState {
        return gattClient?.getConnectionState(address) ?: ConnectionState.DISCONNECTED
    }

    fun shutdown() {
        scanner?.stopScanning()
        advertiser?.stopAdvertising()
        gattClient?.disconnectAll()
        gattServer.stop()
        protocol.clearReassemblyBuffers()
    }
}
