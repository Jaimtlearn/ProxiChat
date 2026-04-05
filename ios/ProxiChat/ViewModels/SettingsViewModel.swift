import Foundation

class SettingsViewModel: ObservableObject {

    var settings: UserSettings
    private let bluetooth: BluetoothController
    private let messageStore: MessageStore

    init(settings: UserSettings, bluetooth: BluetoothController, messageStore: MessageStore) {
        self.settings = settings
        self.bluetooth = bluetooth
        self.messageStore = messageStore
    }

    func updateDisplayName(_ name: String) {
        settings.displayName = name
        bluetooth.updateDisplayName(name)
    }

    func setDiscoverable(_ discoverable: Bool) {
        settings.isDiscoverable = discoverable
        if discoverable {
            bluetooth.peripheralManager.startAdvertising()
        } else {
            bluetooth.peripheralManager.stopAdvertising()
        }
    }

    func clearChatHistory() {
        Task { await messageStore.deleteAllMessages() }
    }
}
