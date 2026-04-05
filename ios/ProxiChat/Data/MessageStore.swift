import Foundation

/// File-based message persistence. Stores messages as JSON per device.
actor MessageStore {

    private let fileManager = FileManager.default
    private var cache: [String: [ChatMessage]] = [:]

    private nonisolated var baseDirectory: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("messages", isDirectory: true)
    }

    init() {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("messages", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    func messages(for deviceID: String) -> [ChatMessage] {
        if let cached = cache[deviceID] { return cached }
        let messages = loadFromDisk(deviceID: deviceID)
        cache[deviceID] = messages
        return messages
    }

    func addMessage(_ message: ChatMessage) {
        let key = message.deviceId
        var messages = cache[key] ?? loadFromDisk(deviceID: key)
        messages.append(message)
        cache[key] = messages
        saveToDisk(deviceID: key, messages: messages)
    }

    func updateMessageStatus(id: String, deviceID: String, status: MessageStatus) {
        var messages = cache[deviceID] ?? loadFromDisk(deviceID: deviceID)
        if let index = messages.firstIndex(where: { $0.id == id }) {
            messages[index].status = status
            cache[deviceID] = messages
            saveToDisk(deviceID: deviceID, messages: messages)
        }
    }

    func deleteMessages(for deviceID: String) {
        cache.removeValue(forKey: deviceID)
        let url = fileURL(for: deviceID)
        try? fileManager.removeItem(at: url)
    }

    func deleteAllMessages() {
        cache.removeAll()
        try? fileManager.removeItem(at: baseDirectory)
        try? fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)
    }

    func latestMessage(for deviceID: String) -> ChatMessage? {
        messages(for: deviceID).last
    }

    // MARK: - Private

    private func fileURL(for deviceID: String) -> URL {
        let safe = deviceID.replacingOccurrences(of: ":", with: "_")
        return baseDirectory.appendingPathComponent("\(safe).json")
    }

    private func loadFromDisk(deviceID: String) -> [ChatMessage] {
        let url = fileURL(for: deviceID)
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([ChatMessage].self, from: data)) ?? []
    }

    private func saveToDisk(deviceID: String, messages: [ChatMessage]) {
        let url = fileURL(for: deviceID)
        if let data = try? JSONEncoder().encode(messages) {
            try? data.write(to: url, options: .atomic)
        }
    }
}
