package com.proxichat.app.domain.repository

import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val discoveredDevices: Flow<List<ChatDevice>>
    val connectedDevices: Flow<List<ChatDevice>>
    val isScanning: Flow<Boolean>

    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun connectToDevice(address: String)
    suspend fun disconnectFromDevice(address: String)
    suspend fun disconnectAll()

    fun getConnectionState(address: String): Flow<ConnectionState>
    fun getDevice(address: String): Flow<ChatDevice?>

    suspend fun saveContact(device: ChatDevice)
    fun getSavedContacts(): Flow<List<ChatDevice>>
    suspend fun deleteContact(address: String)
}
