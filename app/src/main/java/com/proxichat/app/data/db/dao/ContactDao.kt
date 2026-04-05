package com.proxichat.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.proxichat.app.data.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY last_connected DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE address = :address")
    suspend fun getContact(address: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("UPDATE contacts SET display_name = :displayName WHERE address = :address")
    suspend fun updateDisplayName(address: String, displayName: String)

    @Query("UPDATE contacts SET last_connected = :timestamp WHERE address = :address")
    suspend fun updateLastConnected(address: String, timestamp: Long)

    @Query("DELETE FROM contacts WHERE address = :address")
    suspend fun deleteContact(address: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}
