package com.proxichat.app.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxichat.app.data.bluetooth.BluetoothController
import com.proxichat.app.data.preferences.UserPreferences
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ConnectionState
import com.proxichat.app.domain.repository.ChatRepository
import com.proxichat.app.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryUiState(
    val devices: List<ChatDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isBluetoothEnabled: Boolean = true,
    val displayName: String = "User",
    val errorMessage: String? = null
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val chatRepository: ChatRepository,
    private val bluetoothController: BluetoothController,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private var isInitialized = false

    val uiState: StateFlow<DiscoveryUiState> = combine(
        deviceRepository.discoveredDevices,
        deviceRepository.isScanning,
        userPreferences.displayName,
        _errorMessage
    ) { devices, scanning, name, error ->
        DiscoveryUiState(
            devices = devices,
            isScanning = scanning,
            isBluetoothEnabled = bluetoothController.isBluetoothEnabled,
            displayName = name,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiscoveryUiState()
    )

    init {
        // Initialize bluetooth AND THEN start discovery — no race condition
        viewModelScope.launch {
            val name = userPreferences.displayName.first()
            bluetoothController.initialize(name)
            isInitialized = true
            // Auto-start discovery after initialization completes
            startDiscoveryInternal()
        }
    }

    fun startDiscovery() {
        if (!isInitialized) return // Not ready yet, init will auto-start
        viewModelScope.launch {
            startDiscoveryInternal()
        }
    }

    private suspend fun startDiscoveryInternal() {
        if (!bluetoothController.isBluetoothAvailable) {
            _errorMessage.value = "Bluetooth is not available on this device"
            return
        }
        if (!bluetoothController.isBluetoothEnabled) {
            _errorMessage.value = "Please enable Bluetooth"
            return
        }
        _errorMessage.value = null
        deviceRepository.startDiscovery()
    }

    fun stopDiscovery() {
        viewModelScope.launch {
            deviceRepository.stopDiscovery()
        }
    }

    fun connectToDevice(address: String) {
        viewModelScope.launch {
            try {
                deviceRepository.connectToDevice(address)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to connect: ${e.message}"
            }
        }
    }

    fun disconnectFromDevice(address: String) {
        viewModelScope.launch {
            deviceRepository.disconnectFromDevice(address)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            deviceRepository.stopDiscovery()
        }
    }
}
