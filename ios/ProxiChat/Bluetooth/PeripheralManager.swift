import Foundation
import CoreBluetooth
import Combine

/// GATT Server + BLE Advertiser.
/// Equivalent to Android's BleAdvertiser + GattServerManager.
class PeripheralManager: NSObject, ObservableObject {

    @Published var isAdvertising = false

    private var peripheralManager: CBPeripheralManager?
    private var service: CBMutableService?
    private var writeCharacteristic: CBMutableCharacteristic?
    private var notifyCharacteristic: CBMutableCharacteristic?
    private var profileCharacteristic: CBMutableCharacteristic?

    private var subscribedCentrals: [String: CBCentral] = [:]
    private let protocol_ = MessageProtocol()
    private var wantsToAdvertise = false
    private var serviceAdded = false

    let incomingMessages = PassthroughSubject<(senderID: String, data: Data), Never>()
    let connectionEvents = PassthroughSubject<(deviceID: String, connected: Bool), Never>()

    private var displayName: String = "User"

    override init() {
        super.init()
    }

    func start(displayName: String) {
        self.displayName = displayName
        peripheralManager = CBPeripheralManager(delegate: self, queue: .global(qos: .userInitiated))
    }

    func stop() {
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()
        peripheralManager = nil
        subscribedCentrals.removeAll()
        serviceAdded = false
        wantsToAdvertise = false
        DispatchQueue.main.async { self.isAdvertising = false }
    }

    func startAdvertising() {
        wantsToAdvertise = true
        doAdvertiseIfReady()
    }

    func stopAdvertising() {
        wantsToAdvertise = false
        peripheralManager?.stopAdvertising()
        DispatchQueue.main.async { self.isAdvertising = false }
    }

    func updateDisplayName(_ name: String) {
        displayName = name
        profileCharacteristic?.value = name.data(using: .utf8)
        if isAdvertising {
            stopAdvertising()
            startAdvertising()
        }
    }

    func sendNotification(to centralID: String, data: Data) -> Bool {
        guard let central = subscribedCentrals[centralID],
              let characteristic = notifyCharacteristic else { return false }

        let mtu = central.maximumUpdateValueLength
        let chunks = protocol_.chunk(data: data, mtu: mtu)

        for chunk in chunks {
            let sent = peripheralManager?.updateValue(chunk, for: characteristic, onSubscribedCentrals: [central])
            if sent == false { return false }
        }
        return true
    }

    func isSubscribed(_ centralID: String) -> Bool {
        subscribedCentrals[centralID] != nil
    }

    // MARK: - Private

    private func doAdvertiseIfReady() {
        guard wantsToAdvertise,
              serviceAdded,
              let pm = peripheralManager,
              pm.state == .poweredOn else { return }

        let data: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [BluetoothConstants.serviceUUID],
            CBAdvertisementDataLocalNameKey: displayName
        ]
        pm.startAdvertising(data)
    }

    private func setupService() {
        guard let pm = peripheralManager else { return }

        writeCharacteristic = CBMutableCharacteristic(
            type: BluetoothConstants.messageWriteCharUUID,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: .writeable
        )

        notifyCharacteristic = CBMutableCharacteristic(
            type: BluetoothConstants.messageNotifyCharUUID,
            properties: [.notify, .read],
            value: nil,
            permissions: .readable
        )

        profileCharacteristic = CBMutableCharacteristic(
            type: BluetoothConstants.profileCharUUID,
            properties: .read,
            value: displayName.data(using: .utf8),
            permissions: .readable
        )

        service = CBMutableService(type: BluetoothConstants.serviceUUID, primary: true)
        service?.characteristics = [writeCharacteristic!, notifyCharacteristic!, profileCharacteristic!]
        pm.add(service!)
    }
}

// MARK: - CBPeripheralManagerDelegate

extension PeripheralManager: CBPeripheralManagerDelegate {

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("[PeripheralManager] State: \(peripheral.state.rawValue)")
        if peripheral.state == .poweredOn {
            setupService()
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            print("[PeripheralManager] Failed to add service: \(error)")
            return
        }
        print("[PeripheralManager] Service added OK")
        serviceAdded = true
        doAdvertiseIfReady()
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            print("[PeripheralManager] Advertising FAILED: \(error)")
        } else {
            print("[PeripheralManager] Advertising STARTED")
        }
        DispatchQueue.main.async { self.isAdvertising = error == nil }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if request.characteristic.uuid == BluetoothConstants.messageWriteCharUUID,
               let data = request.value {
                let senderID = request.central.identifier.uuidString
                if let assembled = protocol_.reassemble(data) {
                    incomingMessages.send((senderID: senderID, data: assembled))
                }
            }
            peripheral.respond(to: request, withResult: .success)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        if request.characteristic.uuid == BluetoothConstants.profileCharUUID {
            request.value = displayName.data(using: .utf8)
            peripheral.respond(to: request, withResult: .success)
        } else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral,
                           didSubscribeTo characteristic: CBCharacteristic) {
        let id = central.identifier.uuidString
        subscribedCentrals[id] = central
        connectionEvents.send((deviceID: id, connected: true))
        print("[PeripheralManager] Central \(id) subscribed")
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral,
                           didUnsubscribeFrom characteristic: CBCharacteristic) {
        let id = central.identifier.uuidString
        subscribedCentrals.removeValue(forKey: id)
        connectionEvents.send((deviceID: id, connected: false))
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        // Transmit queue has space — could retry pending notifications
    }
}
