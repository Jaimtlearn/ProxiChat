import Foundation
import CoreBluetooth
import Combine

/// BLE Scanner + GATT Client.
/// Equivalent to Android's BleScanner + GattClientManager.
class CentralManager: NSObject, ObservableObject {

    @Published var isScanning = false
    @Published var discoveredDevices: [String: ChatDevice] = [:]
    @Published var connectionStates: [String: ConnectionState] = [:]

    let incomingNotifications = PassthroughSubject<(senderID: String, data: Data), Never>()

    private var centralManager: CBCentralManager?
    private var connectedPeripherals: [String: CBPeripheral] = [:]
    private let protocol_ = MessageProtocol()
    private var pruneTimer: Timer?
    private var wantsToScan = false

    override init() {
        super.init()
    }

    func start() {
        centralManager = CBCentralManager(delegate: self, queue: .global(qos: .userInitiated))
    }

    func stop() {
        stopScanning()
        disconnectAll()
        centralManager = nil
    }

    func startScanning() {
        wantsToScan = true
        doScanIfReady()
    }

    func stopScanning() {
        wantsToScan = false
        centralManager?.stopScan()
        DispatchQueue.main.async { self.isScanning = false }
        pruneTimer?.invalidate()
        pruneTimer = nil
    }

    func connect(to deviceID: String) {
        guard let device = discoveredDevices[deviceID],
              let peripheral = device.peripheral else {
            print("[CentralManager] Cannot connect: device not found")
            return
        }
        updateConnectionState(deviceID, .connecting)
        centralManager?.connect(peripheral, options: [
            CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
        ])
    }

    func disconnect(from deviceID: String) {
        if let peripheral = connectedPeripherals.removeValue(forKey: deviceID) {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        updateConnectionState(deviceID, .disconnected)
    }

    func disconnectAll() {
        for (id, peripheral) in connectedPeripherals {
            centralManager?.cancelPeripheralConnection(peripheral)
            updateConnectionState(id, .disconnected)
        }
        connectedPeripherals.removeAll()
    }

    func sendMessage(to deviceID: String, data: Data) -> Bool {
        guard let peripheral = connectedPeripherals[deviceID],
              let service = peripheral.services?.first(where: { $0.uuid == BluetoothConstants.serviceUUID }),
              let writeChar = service.characteristics?.first(where: { $0.uuid == BluetoothConstants.messageWriteCharUUID })
        else { return false }

        let mtu = peripheral.maximumWriteValueLength(for: .withResponse)
        let chunks = protocol_.chunk(data: data, mtu: max(mtu, 20))

        for chunk in chunks {
            peripheral.writeValue(chunk, for: writeChar, type: .withResponse)
        }
        return true
    }

    func isConnected(_ deviceID: String) -> Bool {
        connectedPeripherals[deviceID] != nil
    }

    func updateDeviceConnectionState(_ deviceID: String, _ state: ConnectionState) {
        updateConnectionState(deviceID, state)
    }

    // MARK: - Private

    private func doScanIfReady() {
        guard wantsToScan,
              let cm = centralManager,
              cm.state == .poweredOn else { return }

        cm.scanForPeripherals(
            withServices: [BluetoothConstants.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
        DispatchQueue.main.async { self.isScanning = true }
        print("[CentralManager] Scan STARTED with UUID filter")
        startPruneTimer()
    }

    private func updateConnectionState(_ deviceID: String, _ state: ConnectionState) {
        DispatchQueue.main.async {
            self.connectionStates[deviceID] = state
            if var device = self.discoveredDevices[deviceID] {
                device.connectionState = state
                self.discoveredDevices[deviceID] = device
            }
        }
    }

    private func startPruneTimer() {
        pruneTimer?.invalidate()
        pruneTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.pruneStaleDevices()
        }
    }

    private func pruneStaleDevices() {
        let cutoff = Date().addingTimeInterval(-BluetoothConstants.deviceStaleTimeoutSeconds)
        DispatchQueue.main.async {
            self.discoveredDevices = self.discoveredDevices.filter { (_, device) in
                device.connectionState == .connected || device.lastSeen > cutoff
            }
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension CentralManager: CBCentralManagerDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        print("[CentralManager] State: \(central.state.rawValue)")
        if central.state == .poweredOn {
            // If scanning was requested before BT powered on, start now
            if wantsToScan {
                doScanIfReady()
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let id = peripheral.identifier.uuidString
        let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let deviceName = peripheral.name ?? "ProxiChat User"

        DispatchQueue.main.async {
            let existing = self.discoveredDevices[id]

            // RSSI smoothing: 70% new + 30% old
            let rssi: Int
            if let old = existing?.rssi {
                rssi = Int(0.7 * Double(RSSI.intValue) + 0.3 * Double(old))
            } else {
                rssi = RSSI.intValue
            }

            let device = ChatDevice(
                id: id,
                name: deviceName,
                displayName: localName ?? existing?.displayName ?? deviceName,
                rssi: rssi,
                connectionState: existing?.connectionState ?? .disconnected,
                lastSeen: Date(),
                peripheral: peripheral
            )
            self.discoveredDevices[id] = device
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let id = peripheral.identifier.uuidString
        connectedPeripherals[id] = peripheral
        peripheral.delegate = self
        peripheral.discoverServices([BluetoothConstants.serviceUUID])
        updateConnectionState(id, .connecting)
        print("[CentralManager] Connected to \(id), discovering services...")
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier.uuidString
        print("[CentralManager] Failed to connect \(id): \(error?.localizedDescription ?? "unknown")")
        updateConnectionState(id, .failed)

        // Auto-retry once after 2 seconds
        DispatchQueue.global().asyncAfter(deadline: .now() + 2.0) { [weak self] in
            if self?.connectedPeripherals[id] == nil {
                print("[CentralManager] Retrying connection to \(id)")
                central.connect(peripheral, options: nil)
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: id)
        updateConnectionState(id, .disconnected)
        print("[CentralManager] Disconnected from \(id)")

        // Auto-reconnect once
        if error != nil {
            DispatchQueue.global().asyncAfter(deadline: .now() + 2.0) { [weak self] in
                if self?.connectedPeripherals[id] == nil,
                   let device = self?.discoveredDevices[id],
                   let p = device.peripheral {
                    print("[CentralManager] Auto-reconnecting to \(id)")
                    central.connect(p, options: nil)
                }
            }
        }
    }
}

// MARK: - CBPeripheralDelegate

extension CentralManager: CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == BluetoothConstants.serviceUUID }) else {
            print("[CentralManager] ProxiChat service NOT found on \(peripheral.identifier)")
            updateConnectionState(peripheral.identifier.uuidString, .failed)
            return
        }
        peripheral.discoverCharacteristics([
            BluetoothConstants.messageWriteCharUUID,
            BluetoothConstants.messageNotifyCharUUID,
            BluetoothConstants.profileCharUUID
        ], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        let id = peripheral.identifier.uuidString

        for char in characteristics {
            if char.uuid == BluetoothConstants.messageNotifyCharUUID {
                peripheral.setNotifyValue(true, for: char)
                print("[CentralManager] Subscribed to notifications on \(id)")
            }
        }

        updateConnectionState(id, .connected)
        print("[CentralManager] Fully connected to \(id)")
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == BluetoothConstants.messageNotifyCharUUID,
              let data = characteristic.value else { return }

        let id = peripheral.identifier.uuidString
        if let assembled = protocol_.reassemble(data) {
            incomingNotifications.send((senderID: id, data: assembled))
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("[CentralManager] Write failed: \(error)")
        }
    }
}
