package com.proxichat.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.proxichat.app.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE device_address = :deviceAddress ORDER BY timestamp ASC")
    fun getMessagesForDevice(deviceAddress: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE device_address = :deviceAddress ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMessageForDevice(deviceAddress: String): Flow<MessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE device_address = :deviceAddress")
    suspend fun deleteMessagesForDevice(deviceAddress: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("SELECT COUNT(*) FROM messages WHERE device_address = :deviceAddress AND is_outgoing = 0 AND status != 'READ'")
    fun getUnreadCountForDevice(deviceAddress: String): Flow<Int>

    @Query("SELECT DISTINCT device_address FROM messages ORDER BY timestamp DESC")
    fun getDeviceAddressesWithMessages(): Flow<List<String>>
}
