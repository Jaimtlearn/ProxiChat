package com.proxichat.app.data.repository

import com.proxichat.app.data.bluetooth.BluetoothController
import com.proxichat.app.data.db.dao.MessageDao
import com.proxichat.app.data.db.entity.MessageEntity
import com.proxichat.app.domain.model.ChatMessage
import com.proxichat.app.domain.model.MessageStatus
import com.proxichat.app.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val bluetoothController: BluetoothController
) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Listen for incoming messages and ACKs from Bluetooth
        scope.launch {
            bluetoothController.receivedMessages.collect { received ->
                when (received.message.type) {
                    "MSG" -> {
                        val text = received.message.payload["text"] as? String ?: return@collect
                        val incomingMessage = ChatMessage(
                            id = received.message.id,
                            deviceAddress = received.senderAddress,
                            text = text,
                            timestamp = received.message.timestamp,
                            isOutgoing = false,
                            status = MessageStatus.DELIVERED
                        )
                        saveIncomingMessage(incomingMessage)
                    }
                    "ACK" -> {
                        val messageId = received.message.payload["messageId"] as? String ?: return@collect
                        val statusStr = received.message.payload["status"] as? String ?: return@collect
                        val status = try {
                            MessageStatus.valueOf(statusStr)
                        } catch (e: IllegalArgumentException) {
                            MessageStatus.DELIVERED
                        }
                        updateMessageStatus(messageId, status)
                    }
                }
            }
        }
    }

    override fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForDevice(deviceAddress).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getLatestMessageForDevice(deviceAddress: String): Flow<ChatMessage?> {
        return messageDao.getLatestMessageForDevice(deviceAddress).map { it?.toDomain() }
    }

    override suspend fun sendMessage(deviceAddress: String, text: String): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            deviceAddress = deviceAddress,
            text = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            status = MessageStatus.SENDING
        )

        // Persist locally
        messageDao.insertMessage(message.toEntity())

        // Send via Bluetooth
        val sent = bluetoothController.sendTextMessage(deviceAddress, text, message.id)

        val finalStatus = if (sent) MessageStatus.SENT else MessageStatus.FAILED
        messageDao.updateMessageStatus(message.id, finalStatus.name)

        return message.copy(status = finalStatus)
    }

    override suspend fun saveIncomingMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status.name)
    }

    override suspend fun deleteMessagesForDevice(deviceAddress: String) {
        messageDao.deleteMessagesForDevice(deviceAddress)
    }

    override suspend fun deleteAllMessages() {
        messageDao.deleteAllMessages()
    }

    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        deviceAddress = deviceAddress,
        text = text,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        status = try { MessageStatus.valueOf(status) } catch (e: IllegalArgumentException) { MessageStatus.FAILED },
        isEncrypted = isEncrypted
    )

    private fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
        id = id,
        deviceAddress = deviceAddress,
        text = text,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        status = status.name,
        isEncrypted = isEncrypted
    )
}
