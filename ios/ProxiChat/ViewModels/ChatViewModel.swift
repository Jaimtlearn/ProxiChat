import Foundation
import Combine

@MainActor
class ChatViewModel: ObservableObject {

    @Published var messages: [ChatMessage] = []
    @Published var inputText = ""
    @Published var connectionState: ConnectionState = .disconnected
    @Published var isRemoteTyping = false
    @Published var device: ChatDevice?

    let deviceID: String
    private let bluetooth: BluetoothController
    private let messageStore: MessageStore
    private var cancellables = Set<AnyCancellable>()

    init(deviceID: String, bluetooth: BluetoothController, messageStore: MessageStore) {
        self.deviceID = deviceID
        self.bluetooth = bluetooth
        self.messageStore = messageStore

        // Load existing messages
        Task { await loadMessages() }

        // Watch for device state
        bluetooth.$discoveredDevices
            .compactMap { $0[deviceID] }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] device in
                self?.device = device
                self?.connectionState = device.connectionState
            }
            .store(in: &cancellables)

        // Watch typing state
        bluetooth.$typingStates
            .map { $0[deviceID] ?? false }
            .receive(on: DispatchQueue.main)
            .assign(to: &$isRemoteTyping)

        // Listen for incoming messages
        bluetooth.receivedMessages
            .filter { $0.senderID == deviceID }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (_, message) in
                Task { await self?.handleReceivedMessage(message) }
            }
            .store(in: &cancellables)
    }

    func sendMessage() {
        let text = inputText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }

        inputText = ""
        let msg = ChatMessage(deviceId: deviceID, text: text, isOutgoing: true, status: .sending)

        Task {
            await messageStore.addMessage(msg)
            await loadMessages()

            let sent = bluetooth.sendTextMessage(to: deviceID, text: text, messageId: msg.id)
            let status: MessageStatus = sent ? .sent : .failed
            await messageStore.updateMessageStatus(id: msg.id, deviceID: deviceID, status: status)
            await loadMessages()
        }
    }

    func retryMessage(_ messageId: String) {
        Task {
            guard let msg = messages.first(where: { $0.id == messageId && $0.status == .failed }) else { return }
            await messageStore.updateMessageStatus(id: messageId, deviceID: deviceID, status: .sending)
            await loadMessages()

            let sent = bluetooth.sendTextMessage(to: deviceID, text: msg.text, messageId: messageId)
            let status: MessageStatus = sent ? .sent : .failed
            await messageStore.updateMessageStatus(id: messageId, deviceID: deviceID, status: status)
            await loadMessages()
        }
    }

    func sendTypingIndicator(_ isTyping: Bool) {
        bluetooth.sendTypingIndicator(to: deviceID, isTyping: isTyping)
    }

    func reconnect() {
        bluetooth.connect(to: deviceID)
    }

    // MARK: - Private

    private func loadMessages() async {
        messages = await messageStore.messages(for: deviceID)
    }

    private func handleReceivedMessage(_ proto: MessageProtocol.ProtocolMessage) async {
        switch proto.t {
        case "MSG":
            guard let text = proto.p["text"]?.stringValue else { return }
            let msg = ChatMessage(
                id: proto.i,
                deviceId: deviceID,
                text: text,
                timestamp: Date(timeIntervalSince1970: TimeInterval(proto.ts) / 1000),
                isOutgoing: false,
                status: .delivered
            )
            await messageStore.addMessage(msg)
            await loadMessages()

        case "ACK":
            guard let msgId = proto.p["messageId"]?.stringValue,
                  let statusStr = proto.p["status"]?.stringValue,
                  let status = MessageStatus(rawValue: statusStr.lowercased()) else { return }
            await messageStore.updateMessageStatus(id: msgId, deviceID: deviceID, status: status)
            await loadMessages()

        default:
            break
        }
    }
}
