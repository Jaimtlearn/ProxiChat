package com.proxichat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxichat.app.data.bluetooth.BluetoothController
import com.proxichat.app.data.preferences.UserPreferences
import com.proxichat.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "User",
    val darkMode: String = "system",
    val isDiscoverable: Boolean = true,
    val autoReconnect: Boolean = true,
    val encryptionEnabled: Boolean = false,
    val appVersion: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bluetoothController: BluetoothController,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferences.displayName,
        userPreferences.darkMode,
        userPreferences.isDiscoverable,
        userPreferences.autoReconnect,
        userPreferences.encryptionEnabled
    ) { name, darkMode, discoverable, autoReconnect, encryption ->
        SettingsUiState(
            displayName = name,
            darkMode = darkMode,
            isDiscoverable = discoverable,
            autoReconnect = autoReconnect,
            encryptionEnabled = encryption
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            userPreferences.setDisplayName(name)
            bluetoothController.updateDisplayName(name)
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            userPreferences.setDarkMode(mode)
        }
    }

    fun setDiscoverable(discoverable: Boolean) {
        viewModelScope.launch {
            userPreferences.setDiscoverable(discoverable)
            if (discoverable) {
                bluetoothController.advertiser?.startAdvertising(uiState.value.displayName)
            } else {
                bluetoothController.advertiser?.stopAdvertising()
            }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAutoReconnect(enabled)
        }
    }

    fun setEncryption(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setEncryptionEnabled(enabled)
            if (enabled) {
                bluetoothController.protocol.generateEncryptionKey()
            } else {
                bluetoothController.protocol.clearEncryption()
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
        }
    }
}
