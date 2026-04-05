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
            .map { devices in
                devices.values.sorted { $0.rssi > $1.rssi }
            }
            .receive(on: DispatchQueue.main)
            .assign(to: &$devices)

        bluetooth.$isScanning
            .receive(on: DispatchQueue.main)
            .assign(to: &$isScanning)
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
