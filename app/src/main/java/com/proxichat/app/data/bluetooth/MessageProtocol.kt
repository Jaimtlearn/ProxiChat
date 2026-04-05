package com.proxichat.app.data.bluetooth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Handles message serialization, chunking, encryption, and reassembly for BLE transport.
 */
class MessageProtocol(private val gson: Gson = Gson()) {

    private val sequenceCounter = AtomicInteger(0)
    private val reassemblyBuffers = ConcurrentHashMap<Int, MutableList<ChunkData>>()
    private var encryptionKey: SecretKey? = null

    // --- Data classes for the wire protocol ---

    data class ProtocolMessage(
        @SerializedName("t") val type: String,
        @SerializedName("i") val id: String = UUID.randomUUID().toString(),
        @SerializedName("s") val sender: String = "",
        @SerializedName("ts") val timestamp: Long = System.currentTimeMillis(),
        @SerializedName("p") val payload: Map<String, Any?> = emptyMap(),
        @SerializedName("e") val encrypted: Boolean = false
    )

    data class ChunkData(
        val sequenceNumber: Int,
        val chunkIndex: Int,
        val flags: Byte,
        val data: ByteArray
    ) {
        val isFirst: Boolean get() = (flags.toInt() and BluetoothConstants.FLAG_FIRST_CHUNK.toInt()) != 0
        val isLast: Boolean get() = (flags.toInt() and BluetoothConstants.FLAG_LAST_CHUNK.toInt()) != 0
        val isSingle: Boolean get() = isFirst && isLast

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChunkData) return false
            return sequenceNumber == other.sequenceNumber && chunkIndex == other.chunkIndex
        }

        override fun hashCode(): Int = 31 * sequenceNumber + chunkIndex
    }

    // --- Message creation helpers ---

    fun createTextMessage(text: String, senderAddress: String): ProtocolMessage {
        return ProtocolMessage(
            type = "MSG",
            sender = senderAddress,
            payload = mapOf("text" to text)
        )
    }

    fun createAckMessage(messageId: String, status: String, senderAddress: String): ProtocolMessage {
        return ProtocolMessage(
            type = "ACK",
            sender = senderAddress,
            payload = mapOf("messageId" to messageId, "status" to status)
        )
    }

    fun createTypingMessage(isTyping: Boolean, senderAddress: String): ProtocolMessage {
        return ProtocolMessage(
            type = "TYPING",
            sender = senderAddress,
            payload = mapOf("isTyping" to isTyping)
        )
    }

    fun createProfileMessage(displayName: String, avatarIndex: Int, senderAddress: String): ProtocolMessage {
        return ProtocolMessage(
            type = "PROFILE",
            sender = senderAddress,
            payload = mapOf("displayName" to displayName, "avatarIndex" to avatarIndex)
        )
    }

    fun createDisconnectMessage(senderAddress: String): ProtocolMessage {
        return ProtocolMessage(
            type = "DISCONNECT",
            sender = senderAddress
        )
    }

    // --- Serialization ---

    fun serialize(message: ProtocolMessage): ByteArray {
        val json = gson.toJson(message)
        val data = if (encryptionKey != null && message.type == "MSG") {
            val encrypted = encrypt(json.toByteArray(Charsets.UTF_8))
            val wrappedMessage = message.copy(
                payload = mapOf("data" to Base64.encodeToString(encrypted, Base64.NO_WRAP)),
                encrypted = true
            )
            gson.toJson(wrappedMessage).toByteArray(Charsets.UTF_8)
        } else {
            json.toByteArray(Charsets.UTF_8)
        }
        return data
    }

    fun deserialize(data: ByteArray): ProtocolMessage? {
        return try {
            val json = String(data, Charsets.UTF_8)
            val message = gson.fromJson(json, ProtocolMessage::class.java)
            if (message.encrypted && encryptionKey != null) {
                val encryptedData = Base64.decode(message.payload["data"] as String, Base64.NO_WRAP)
                val decrypted = decrypt(encryptedData)
                gson.fromJson(String(decrypted, Charsets.UTF_8), ProtocolMessage::class.java)
            } else {
                message
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- Chunking ---

    fun chunk(data: ByteArray, mtu: Int): List<ByteArray> {
        val maxPayloadSize = mtu - BluetoothConstants.CHUNK_HEADER_SIZE
        if (maxPayloadSize <= 0) return listOf(data)

        val sequence = sequenceCounter.getAndIncrement() and 0xFFFF

        if (data.size <= maxPayloadSize) {
            return listOf(createChunk(sequence, 0, BluetoothConstants.FLAG_SINGLE_CHUNK, data))
        }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var chunkIndex = 0

        while (offset < data.size) {
            val remaining = data.size - offset
            val chunkSize = minOf(remaining, maxPayloadSize)
            val chunkData = data.copyOfRange(offset, offset + chunkSize)

            val flags: Byte = when {
                offset == 0 -> BluetoothConstants.FLAG_FIRST_CHUNK
                offset + chunkSize >= data.size -> BluetoothConstants.FLAG_LAST_CHUNK
                else -> 0
            }

            chunks.add(createChunk(sequence, chunkIndex, flags, chunkData))
            offset += chunkSize
            chunkIndex++
        }

        return chunks
    }

    fun reassemble(chunkBytes: ByteArray): ByteArray? {
        val chunk = parseChunk(chunkBytes) ?: return null

        if (chunk.isSingle) {
            return chunk.data
        }

        val buffer = reassemblyBuffers.getOrPut(chunk.sequenceNumber) { mutableListOf() }
        buffer.add(chunk)

        if (chunk.isLast) {
            reassemblyBuffers.remove(chunk.sequenceNumber)
            val sorted = buffer.sortedBy { it.chunkIndex }
            val totalSize = sorted.sumOf { it.data.size }
            val result = ByteArray(totalSize)
            var offset = 0
            for (c in sorted) {
                c.data.copyInto(result, offset)
                offset += c.data.size
            }
            return result
        }

        return null // Still waiting for more chunks
    }

    private fun createChunk(sequence: Int, index: Int, flags: Byte, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(BluetoothConstants.CHUNK_HEADER_SIZE + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(BluetoothConstants.PROTOCOL_VERSION)
        buffer.putShort(sequence.toShort())
        buffer.put(flags)
        buffer.put(index.toByte())
        buffer.put(data)
        return buffer.array()
    }

    private fun parseChunk(bytes: ByteArray): ChunkData? {
        if (bytes.size < BluetoothConstants.CHUNK_HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.get()
        if (version != BluetoothConstants.PROTOCOL_VERSION) return null
        val sequence = buffer.short.toInt() and 0xFFFF
        val flags = buffer.get()
        val index = buffer.get().toInt() and 0xFF
        val data = ByteArray(bytes.size - BluetoothConstants.CHUNK_HEADER_SIZE)
        buffer.get(data)
        return ChunkData(sequence, index, flags, data)
    }

    // --- Encryption (AES-256-GCM) ---

    fun generateEncryptionKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        encryptionKey = key
        return key
    }

    fun setEncryptionKey(keyBytes: ByteArray) {
        encryptionKey = SecretKeySpec(keyBytes, "AES")
    }

    fun getEncryptionKeyBytes(): ByteArray? = encryptionKey?.encoded

    fun isEncryptionEnabled(): Boolean = encryptionKey != null

    fun clearEncryption() {
        encryptionKey = null
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val key = encryptionKey ?: throw IllegalStateException("Encryption key not set")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        // Prepend IV length + IV + encrypted data
        return ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val key = encryptionKey ?: throw IllegalStateException("Encryption key not set")
        val buffer = ByteBuffer.wrap(data)
        val ivLength = buffer.get().toInt() and 0xFF
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encrypted = ByteArray(data.size - 1 - ivLength)
        buffer.get(encrypted)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    fun clearReassemblyBuffers() {
        reassemblyBuffers.clear()
    }
}
