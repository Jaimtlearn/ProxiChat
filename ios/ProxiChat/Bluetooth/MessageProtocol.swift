import Foundation
import CommonCrypto

/// Handles message serialization, chunking, encryption, and reassembly for BLE transport.
/// Wire-compatible with the Android implementation.
class MessageProtocol {

    struct ProtocolMessage: Codable {
        let t: String        // type: MSG, ACK, TYPING, PROFILE, DISCONNECT
        let i: String        // id
        let s: String        // sender
        let ts: Int64        // timestamp (ms since epoch)
        let p: [String: AnyCodable] // payload
        let e: Bool          // encrypted

        init(type: String, id: String = UUID().uuidString, sender: String = "",
             timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
             payload: [String: AnyCodable] = [:], encrypted: Bool = false) {
            self.t = type
            self.i = id
            self.s = sender
            self.ts = timestamp
            self.p = payload
            self.e = encrypted
        }
    }

    private var sequenceCounter: UInt16 = 0
    private var reassemblyBuffers: [UInt16: [(index: Int, data: Data)]] = [:]
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    // MARK: - Message Creators

    func createTextMessage(text: String, sender: String) -> ProtocolMessage {
        ProtocolMessage(type: "MSG", sender: sender, payload: ["text": AnyCodable(text)])
    }

    func createAckMessage(messageId: String, status: String, sender: String) -> ProtocolMessage {
        ProtocolMessage(type: "ACK", sender: sender, payload: [
            "messageId": AnyCodable(messageId),
            "status": AnyCodable(status)
        ])
    }

    func createTypingMessage(isTyping: Bool, sender: String) -> ProtocolMessage {
        ProtocolMessage(type: "TYPING", sender: sender, payload: ["isTyping": AnyCodable(isTyping)])
    }

    func createProfileMessage(displayName: String, avatarIndex: Int, sender: String) -> ProtocolMessage {
        ProtocolMessage(type: "PROFILE", sender: sender, payload: [
            "displayName": AnyCodable(displayName),
            "avatarIndex": AnyCodable(avatarIndex)
        ])
    }

    func createDisconnectMessage(sender: String) -> ProtocolMessage {
        ProtocolMessage(type: "DISCONNECT", sender: sender)
    }

    // MARK: - Serialization

    func serialize(_ message: ProtocolMessage) -> Data? {
        try? encoder.encode(message)
    }

    func deserialize(_ data: Data) -> ProtocolMessage? {
        try? decoder.decode(ProtocolMessage.self, from: data)
    }

    // MARK: - Chunking

    func chunk(data: Data, mtu: Int) -> [Data] {
        let maxPayload = mtu - BluetoothConstants.chunkHeaderSize
        guard maxPayload > 0 else { return [data] }

        let sequence = sequenceCounter
        sequenceCounter &+= 1

        if data.count <= maxPayload {
            return [createChunk(sequence: sequence, index: 0,
                               flags: BluetoothConstants.flagSingleChunk, payload: data)]
        }

        var chunks: [Data] = []
        var offset = 0
        var chunkIndex: UInt8 = 0

        while offset < data.count {
            let remaining = data.count - offset
            let chunkSize = min(remaining, maxPayload)
            let chunkPayload = data[offset..<(offset + chunkSize)]

            let flags: UInt8
            if offset == 0 {
                flags = BluetoothConstants.flagFirstChunk
            } else if offset + chunkSize >= data.count {
                flags = BluetoothConstants.flagLastChunk
            } else {
                flags = 0
            }

            chunks.append(createChunk(sequence: sequence, index: chunkIndex,
                                      flags: flags, payload: Data(chunkPayload)))
            offset += chunkSize
            chunkIndex &+= 1
        }

        return chunks
    }

    func reassemble(_ chunkData: Data) -> Data? {
        guard chunkData.count >= BluetoothConstants.chunkHeaderSize else { return nil }

        let version = chunkData[0]
        guard version == BluetoothConstants.protocolVersion else { return nil }

        let sequence = UInt16(chunkData[1]) | (UInt16(chunkData[2]) << 8)
        let flags = chunkData[3]
        let index = Int(chunkData[4])
        let payload = chunkData.dropFirst(BluetoothConstants.chunkHeaderSize)

        let isFirst = (flags & BluetoothConstants.flagFirstChunk) != 0
        let isLast = (flags & BluetoothConstants.flagLastChunk) != 0

        // Single chunk message
        if isFirst && isLast {
            return Data(payload)
        }

        // Multi-chunk: accumulate
        if reassemblyBuffers[sequence] == nil {
            reassemblyBuffers[sequence] = []
        }
        reassemblyBuffers[sequence]?.append((index: index, data: Data(payload)))

        if isLast {
            guard let chunks = reassemblyBuffers.removeValue(forKey: sequence) else { return nil }
            let sorted = chunks.sorted { $0.index < $1.index }
            var result = Data()
            for chunk in sorted {
                result.append(chunk.data)
            }
            return result
        }

        return nil // Still waiting for more chunks
    }

    // MARK: - Private

    private func createChunk(sequence: UInt16, index: UInt8, flags: UInt8, payload: Data) -> Data {
        var chunk = Data(capacity: BluetoothConstants.chunkHeaderSize + payload.count)
        chunk.append(BluetoothConstants.protocolVersion)
        chunk.append(UInt8(sequence & 0xFF))
        chunk.append(UInt8((sequence >> 8) & 0xFF))
        chunk.append(flags)
        chunk.append(index)
        chunk.append(payload)
        return chunk
    }

    func clearBuffers() {
        reassemblyBuffers.removeAll()
    }
}

// MARK: - AnyCodable helper for heterogeneous JSON payloads

struct AnyCodable: Codable {
    let value: Any

    init(_ value: Any) { self.value = value }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let b = try? container.decode(Bool.self) { value = b }
        else if let i = try? container.decode(Int.self) { value = i }
        else if let d = try? container.decode(Double.self) { value = d }
        else if let s = try? container.decode(String.self) { value = s }
        else { value = "" }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let b as Bool: try container.encode(b)
        case let i as Int: try container.encode(i)
        case let d as Double: try container.encode(d)
        case let s as String: try container.encode(s)
        default: try container.encodeNil()
        }
    }

    var stringValue: String? { value as? String }
    var boolValue: Bool? { value as? Bool }
    var intValue: Int? { value as? Int }
}
