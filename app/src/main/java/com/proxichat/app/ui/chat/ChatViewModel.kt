package com.proxichat.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxichat.app.data.bluetooth.BluetoothController
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ChatMessage
import com.proxichat.app.domain.model.ConnectionState
import com.proxichat.app.domain.model.MessageStatus
import com.proxichat.app.domain.repository.ChatRepository
import com.proxichat.app.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val device: ChatDevice? = null,
    val messages: List<ChatMessage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isRemoteTyping: Boolean = false,
    val inputText: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val deviceRepository: DeviceRepository,
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val deviceAddress: String = savedStateHandle.get<String>("deviceAddress") ?: ""

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        deviceRepository.getDevice(deviceAddress),
        chatRepository.getMessagesForDevice(deviceAddress),
        deviceRepository.getConnectionState(deviceAddress),
        bluetoothController.typingStates,
        _errorMessage
    ) { device, messages, connectionState, typingStates, error ->
        ChatUiState(
            device = device,
            messages = messages,
            connectionState = connectionState,
            isRemoteTyping = typingStates[deviceAddress] == true,
            inputText = _inputText.value,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(deviceAddress, text)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send: ${e.message}"
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val messages = uiState.value.messages
            val message = messages.find { it.id == messageId } ?: return@launch
            if (message.status == MessageStatus.FAILED) {
                chatRepository.updateMessageStatus(messageId, MessageStatus.SENDING)
                val sent = bluetoothController.sendTextMessage(deviceAddress, message.text, messageId)
                val newStatus = if (sent) MessageStatus.SENT else MessageStatus.FAILED
                chatRepository.updateMessageStatus(messageId, newStatus)
            }
        }
    }

    fun sendTypingIndicator(isTyping: Boolean) {
        bluetoothController.sendTypingIndicator(deviceAddress, isTyping)
    }

    fun reconnect() {
        viewModelScope.launch {
            deviceRepository.connectToDevice(deviceAddress)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
