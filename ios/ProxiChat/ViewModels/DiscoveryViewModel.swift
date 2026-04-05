import Foundation
import Combine

class DiscoveryViewModel: ObservableObject {

    @Published var devices: [ChatDevice] = []
    @Published var isScanning = false
    @Published var errorMessage: String?

    private let bluetooth: BluetoothController
    private let settings: UserSettings
    private var cancellables = Set<AnyCancellable>()

    init(bluetooth: BluetoothController, settings: UserSettings) {
        self.bluetooth = bluetooth
        self.settings = settings

        bluetooth.$discoveredDevices
            .map { $0.values.sorted { $0.rssi > $1.rssi } }
            .receive(on: DispatchQueue.main)
            .assign(to: &$devices)

        bluetooth.$isScanning
            .receive(on: DispatchQueue.main)
            .assign(to: &$isScanning)

        // Initialize and start discovery immediately.
        // The underlying managers use wantsToScan/wantsToAdvertise flags,
        // so calling startDiscovery() before BT powers on is safe — they queue it.
        bluetooth.initialize(displayName: settings.displayName)
        bluetooth.startDiscovery()
    }

    var connectedDevices: [ChatDevice] {
        devices.filter { $0.connectionState == .connected }
    }

    var nearbyDevices: [ChatDevice] {
        devices.filter { $0.connectionState != .connected }
    }

    func startDiscovery() {
        bluetooth.startDiscovery()
    }

    func stopDiscovery() {
        bluetooth.stopDiscovery()
    }

    func connect(to deviceID: String) {
        bluetooth.connect(to: deviceID)
    }

    func disconnect(from deviceID: String) {
        bluetooth.disconnect(from: deviceID)
    }
}
