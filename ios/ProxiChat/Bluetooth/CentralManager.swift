import Foundation
import CoreBluetooth
import Combine

/// Manages the CBCentralManager side: BLE scanning and GATT client connections.
/// Equivalent to Android's BleScanner + GattClientManager.
class CentralManager: NSObject, ObservableObject {

    @Published var isScanning = false
    @Published var discoveredDevices: [String: ChatDevice] = [:]
    @Published var connectionStates: [String: ConnectionState] = [:]

    let incomingNotifications = PassthroughSubject<(senderID: String, data: Data), Never>()

    private var centralManager: CBCentralManager?
    private var connectedPeripherals: [String: CBPeripheral] = [:]
    private var peripheralMTUs: [String: Int] = [:]
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
        guard let cm = centralManager, cm.state == .poweredOn else { return }
        cm.scanForPeripherals(
            withServices: [BluetoothConstants.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
        DispatchQueue.main.async { self.isScanning = true }
        startPruneTimer()
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
              let peripheral = device.peripheral else { return }

        updateConnectionState(deviceID, .connecting)
        centralManager?.connect(peripheral, options: nil)
    }

    func disconnect(from deviceID: String) {
        if let peripheral = connectedPeripherals.removeValue(forKey: deviceID) {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        peripheralMTUs.removeValue(forKey: deviceID)
        updateConnectionState(deviceID, .disconnected)
    }

    func disconnectAll() {
        for (id, peripheral) in connectedPeripherals {
            centralManager?.cancelPeripheralConnection(peripheral)
            updateConnectionState(id, .disconnected)
        }
        connectedPeripherals.removeAll()
        peripheralMTUs.removeAll()
    }

    func sendMessage(to deviceID: String, data: Data) -> Bool {
        guard let peripheral = connectedPeripherals[deviceID] else { return false }
        guard let service = peripheral.services?.first(where: { $0.uuid == BluetoothConstants.serviceUUID }),
              let writeChar = service.characteristics?.first(where: { $0.uuid == BluetoothConstants.messageWriteCharUUID })
        else { return false }

        let mtu = peripheral.maximumWriteValueLength(for: .withResponse)
        let chunks = protocol_.chunk(data: data, mtu: mtu)

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
        if central.state == .poweredOn {
            // Auto-start scanning when Bluetooth becomes available
            if wantsToScan {
                startScanning()
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let id = peripheral.identifier.uuidString
        let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let deviceName = peripheral.name ?? "Unknown Device"

        DispatchQueue.main.async {
            let existing = self.discoveredDevices[id]

            // Smooth RSSI to prevent jittering (30% new, 70% old)
            let smoothedRssi: Int
            if let existingRssi = existing?.rssi {
                smoothedRssi = Int(Double(existingRssi) * 0.7 + Double(RSSI.intValue) * 0.3)
            } else {
                smoothedRssi = RSSI.intValue
            }

            let device = ChatDevice(
                id: id,
                name: deviceName,
                displayName: localName ?? existing?.displayName ?? deviceName,
                rssi: smoothedRssi,
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
        updateConnectionState(id, .connecting) // Still discovering services
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        updateConnectionState(peripheral.identifier.uuidString, .failed)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: id)
        peripheralMTUs.removeValue(forKey: id)
        updateConnectionState(id, .disconnected)
    }
}

// MARK: - CBPeripheralDelegate

extension CentralManager: CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == BluetoothConstants.serviceUUID }) else {
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

        for characteristic in characteristics {
            if characteristic.uuid == BluetoothConstants.messageNotifyCharUUID {
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }

        // Connection is fully set up
        updateConnectionState(id, .connected)
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
        // Write confirmed
    }
}
