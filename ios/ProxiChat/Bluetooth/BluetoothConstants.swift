import Foundation
import CoreBluetooth

/// Shared BLE constants — must match Android implementation for cross-platform compatibility.
enum BluetoothConstants {
    // Custom service UUID for ProxiChat app identification
    static let serviceUUID = CBUUID(string: "a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    // Characteristic for writing messages (client → server)
    static let messageWriteCharUUID = CBUUID(string: "a1b2c3d4-e5f6-7890-abcd-ef1234567891")

    // Characteristic for receiving messages via notifications (server → client)
    static let messageNotifyCharUUID = CBUUID(string: "a1b2c3d4-e5f6-7890-abcd-ef1234567892")

    // Characteristic for user profile data (read)
    static let profileCharUUID = CBUUID(string: "a1b2c3d4-e5f6-7890-abcd-ef1234567893")

    // Protocol version — must match Android
    static let protocolVersion: UInt8 = 1

    // Chunk header size: version(1) + sequence(2) + flags(1) + index(1)
    static let chunkHeaderSize = 5

    // Default BLE MTU
    static let defaultMTU = 23
    static let preferredMTU = 512

    // Chunk flags
    static let flagFirstChunk: UInt8 = 0x01
    static let flagLastChunk: UInt8 = 0x02
    static let flagSingleChunk: UInt8 = 0x03 // FIRST | LAST

    // Timeouts
    static let scanDurationSeconds: TimeInterval = 12
    static let scanIntervalSeconds: TimeInterval = 15
    static let connectionTimeoutSeconds: TimeInterval = 10
    static let reconnectDelaySeconds: TimeInterval = 3
    static let maxReconnectAttempts = 5
    static let deviceStaleTimeoutSeconds: TimeInterval = 30
}
