package com.proxichat.app.domain.repository

import com.proxichat.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessage>>
    fun getLatestMessageForDevice(deviceAddress: String): Flow<ChatMessage?>
    suspend fun sendMessage(deviceAddress: String, text: String): ChatMessage
    suspend fun saveIncomingMessage(message: ChatMessage)
    suspend fun updateMessageStatus(messageId: String, status: com.proxichat.app.domain.model.MessageStatus)
    suspend fun deleteMessagesForDevice(deviceAddress: String)
    suspend fun deleteAllMessages()
}
