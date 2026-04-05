package com.proxichat.app.data.repository

import com.proxichat.app.data.bluetooth.BluetoothController
import com.proxichat.app.data.db.dao.ContactDao
import com.proxichat.app.data.db.entity.ContactEntity
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ConnectionState
import com.proxichat.app.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val contactDao: ContactDao
) : DeviceRepository {

    override val discoveredDevices: Flow<List<ChatDevice>>
        get() = bluetoothController.discoveredDevices.map { it.values.toList().sortedByDescending { d -> d.rssi } }

    override val connectedDevices: Flow<List<ChatDevice>>
        get() = bluetoothController.discoveredDevices.map { devices ->
            devices.values.filter { it.connectionState == ConnectionState.CONNECTED }
        }

    override val isScanning: Flow<Boolean>
        get() = bluetoothController.isScanning

    override suspend fun startDiscovery() {
        bluetoothController.startDiscovery()
    }

    override suspend fun stopDiscovery() {
        bluetoothController.stopDiscovery()
    }

    override suspend fun connectToDevice(address: String) {
        val connected = bluetoothController.connectToDevice(address)
        if (connected) {
            val device = bluetoothController.discoveredDevices.value[address]
            if (device != null) {
                saveContact(device)
            }
        }
    }

    override suspend fun disconnectFromDevice(address: String) {
        bluetoothController.disconnectFromDevice(address)
    }

    override suspend fun disconnectAll() {
        bluetoothController.disconnectAll()
    }

    override fun getConnectionState(address: String): Flow<ConnectionState> {
        return bluetoothController.discoveredDevices.map { devices ->
            devices[address]?.connectionState ?: ConnectionState.DISCONNECTED
        }
    }

    override fun getDevice(address: String): Flow<ChatDevice?> {
        return bluetoothController.discoveredDevices.map { it[address] }
    }

    override suspend fun saveContact(device: ChatDevice) {
        contactDao.insertContact(
            ContactEntity(
                address = device.address,
                deviceName = device.name,
                displayName = device.displayName,
                avatarColorIndex = device.avatarColorIndex,
                lastConnected = System.currentTimeMillis()
            )
        )
    }

    override fun getSavedContacts(): Flow<List<ChatDevice>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { entity ->
                ChatDevice(
                    address = entity.address,
                    name = entity.deviceName,
                    displayName = entity.displayName,
                    avatarColorIndex = entity.avatarColorIndex,
                    lastSeen = entity.lastConnected
                )
            }
        }
    }

    override suspend fun deleteContact(address: String) {
        contactDao.deleteContact(address)
    }
}
