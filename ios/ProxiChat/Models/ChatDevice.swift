import Foundation
import CoreBluetooth

struct ChatDevice: Identifiable, Hashable {
    let id: String // CBPeripheral identifier or address
    var name: String
    var displayName: String
    var rssi: Int
    var connectionState: ConnectionState
    var lastSeen: Date
    var avatarColorIndex: Int
    var unreadCount: Int
    var lastMessage: String?
    var lastMessageTime: Date?
    var peripheral: CBPeripheral?

    var signalStrength: SignalStrength {
        switch rssi {
        case -50...0: return .excellent
        case -65...(-51): return .good
        case -80...(-66): return .fair
        default: return .weak
        }
    }

    var isConnected: Bool {
        connectionState == .connected
    }

    init(
        id: String,
        name: String = "Unknown",
        displayName: String? = nil,
        rssi: Int = -100,
        connectionState: ConnectionState = .disconnected,
        lastSeen: Date = Date(),
        avatarColorIndex: Int = 0,
        unreadCount: Int = 0,
        lastMessage: String? = nil,
        lastMessageTime: Date? = nil,
        peripheral: CBPeripheral? = nil
    ) {
        self.id = id
        self.name = name
        self.displayName = displayName ?? name
        self.rssi = rssi
        self.connectionState = connectionState
        self.lastSeen = lastSeen
        self.avatarColorIndex = abs(id.hashValue % 8)
        self.unreadCount = unreadCount
        self.lastMessage = lastMessage
        self.lastMessageTime = lastMessageTime
        self.peripheral = peripheral
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: ChatDevice, rhs: ChatDevice) -> Bool {
        lhs.id == rhs.id
    }
}
