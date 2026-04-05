package com.proxichat.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["device_address"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "device_address")
    val deviceAddress: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_outgoing")
    val isOutgoing: Boolean,

    @ColumnInfo(name = "status")
    val status: String, // SENDING, SENT, DELIVERED, READ, FAILED

    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = false
)
