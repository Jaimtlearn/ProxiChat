package com.proxichat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.proxichat.app.data.db.dao.ContactDao
import com.proxichat.app.data.db.dao.MessageDao
import com.proxichat.app.data.db.entity.ContactEntity
import com.proxichat.app.data.db.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, ContactEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
}
