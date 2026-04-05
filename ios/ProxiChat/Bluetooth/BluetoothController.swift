import Foundation
import Combine

/// Central coordinator for all Bluetooth operations on iOS.
class BluetoothController: ObservableObject {

    @Published var isScanning = false
    @Published var discoveredDevices: [String: ChatDevice] = [:]
    @Published var typingStates: [String: Bool] = [:]

    let receivedMessages = PassthroughSubject<(senderID: String, message: MessageProtocol.ProtocolMessage), Never>()
    let connectionEvents = PassthroughSubject<(deviceID: String, state: ConnectionState), Never>()

    let peripheralManager = PeripheralManager()
    let centralManager = CentralManager()
    let protocol_ = MessageProtocol()

    private var cancellables = Set<AnyCancellable>()
    private var displayName = "User"
    private var isInitialized = false

    func initialize(displayName: String) {
        // Idempotent — only initialize once
        if isInitialized {
            self.displayName = displayName
            peripheralManager.updateDisplayName(displayName)
            return
        }
        isInitialized = true
        self.displayName = displayName

        peripheralManager.start(displayName: displayName)
        centralManager.start()

        // Forward scanning state
        centralManager.$isScanning
            .receive(on: DispatchQueue.main)
            .assign(to: &$isScanning)

        // Forward discovered devices
        centralManager.$discoveredDevices
            .receive(on: DispatchQueue.main)
            .assign(to: &$discoveredDevices)

        // Handle incoming messages from peripheral manager (devices writing to us)
        peripheralManager.incomingMessages
            .sink { [weak self] (senderID, data) in
                self?.handleIncomingData(senderID: senderID, data: data)
            }
            .store(in: &cancellables)

        // Handle incoming notifications from central manager (devices notifying us)
        centralManager.incomingNotifications
            .sink { [weak self] (senderID, data) in
                self?.handleIncomingData(senderID: senderID, data: data)
            }
            .store(in: &cancellables)

        // Forward peripheral connection events
        peripheralManager.connectionEvents
            .sink { [weak self] (deviceID, connected) in
                let state: ConnectionState = connected ? .connected : .disconnected
                self?.centralManager.updateDeviceConnectionState(deviceID, state)
                self?.connectionEvents.send((deviceID, state))
            }
            .store(in: &cancellables)
    }

    func startDiscovery() {
        peripheralManager.startAdvertising()
        centralManager.startScanning()
    }

    func stopDiscovery() {
        centralManager.stopScanning()
    }

    func connect(to deviceID: String) {
        centralManager.connect(to: deviceID)
    }

    func disconnect(from deviceID: String) {
        centralManager.disconnect(from: deviceID)
    }

    func disconnectAll() {
        centralManager.disconnectAll()
    }

    func sendTextMessage(to deviceID: String, text: String, messageId: String) -> Bool {
        let msg = protocol_.createTextMessage(text: text, sender: "local")
        let modifiedMsg = MessageProtocol.ProtocolMessage(
            type: msg.t, id: messageId, sender: msg.s,
            timestamp: msg.ts, payload: msg.p, encrypted: msg.e
        )
        guard let data = protocol_.serialize(modifiedMsg) else { return false }
        return sendData(to: deviceID, data: data)
    }

    func sendAck(to deviceID: String, messageId: String, status: String) {
        let msg = protocol_.createAckMessage(messageId: messageId, status: status, sender: "local")
        if let data = protocol_.serialize(msg) {
            _ = sendData(to: deviceID, data: data)
        }
    }

    func sendTypingIndicator(to deviceID: String, isTyping: Bool) {
        let msg = protocol_.createTypingMessage(isTyping: isTyping, sender: "local")
        if let data = protocol_.serialize(msg) {
            _ = sendData(to: deviceID, data: data)
        }
    }

    func updateDisplayName(_ name: String) {
        displayName = name
        peripheralManager.updateDisplayName(name)
    }

    func getConnectionState(for deviceID: String) -> ConnectionState {
        centralManager.connectionStates[deviceID] ?? .disconnected
    }

    func shutdown() {
        peripheralManager.stop()
        centralManager.stop()
        cancellables.removeAll()
        protocol_.clearBuffers()
        isInitialized = false
    }

    // MARK: - Private

    private func sendData(to deviceID: String, data: Data) -> Bool {
        if centralManager.isConnected(deviceID) {
            return centralManager.sendMessage(to: deviceID, data: data)
        }
        if peripheralManager.isSubscribed(deviceID) {
            return peripheralManager.sendNotification(to: deviceID, data: data)
        }
        return false
    }

    private func handleIncomingData(senderID: String, data: Data) {
        guard let message = protocol_.deserialize(data) else { return }

        switch message.t {
        case "MSG":
            receivedMessages.send((senderID, message))
            sendAck(to: senderID, messageId: message.i, status: "DELIVERED")

        case "ACK":
            receivedMessages.send((senderID, message))

        case "TYPING":
            let isTyping = message.p["isTyping"]?.boolValue ?? false
            DispatchQueue.main.async { self.typingStates[senderID] = isTyping }

        case "PROFILE":
            if let name = message.p["displayName"]?.stringValue {
                DispatchQueue.main.async {
                    if var device = self.discoveredDevices[senderID] {
                        device.displayName = name
                        self.discoveredDevices[senderID] = device
                    }
                }
            }

        case "DISCONNECT":
            disconnect(from: senderID)

        default:
            break
        }
    }
}
