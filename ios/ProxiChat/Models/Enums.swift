import Foundation
import SwiftUI

enum ConnectionState: String, Codable {
    case disconnected
    case connecting
    case connected
    case disconnecting
    case failed
}

enum MessageStatus: String, Codable {
    case sending
    case sent
    case delivered
    case read
    case failed
}

enum SignalStrength: String {
    case excellent
    case good
    case fair
    case weak

    var bars: Int {
        switch self {
        case .excellent: return 4
        case .good: return 3
        case .fair: return 2
        case .weak: return 1
        }
    }

    var label: String {
        rawValue.capitalized
    }

    var color: Color {
        switch self {
        case .excellent: return .green
        case .good: return Color(red: 0.54, green: 0.76, blue: 0.29)
        case .fair: return .yellow
        case .weak: return .orange
        }
    }
}

enum DarkModeSetting: String, CaseIterable {
    case system = "system"
    case on = "on"
    case off = "off"

    var label: String {
        switch self {
        case .system: return "Follow System"
        case .on: return "Always On"
        case .off: return "Always Off"
        }
    }
}
