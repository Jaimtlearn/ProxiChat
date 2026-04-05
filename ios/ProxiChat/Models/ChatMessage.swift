import Foundation

struct ChatMessage: Identifiable, Codable, Equatable {
    let id: String
    let deviceId: String
    let text: String
    let timestamp: Date
    let isOutgoing: Bool
    var status: MessageStatus
    var isEncrypted: Bool

    init(
        id: String = UUID().uuidString,
        deviceId: String,
        text: String,
        timestamp: Date = Date(),
        isOutgoing: Bool,
        status: MessageStatus = .sending,
        isEncrypted: Bool = false
    ) {
        self.id = id
        self.deviceId = deviceId
        self.text = text
        self.timestamp = timestamp
        self.isOutgoing = isOutgoing
        self.status = isOutgoing ? status : .delivered
        self.isEncrypted = isEncrypted
    }
}
