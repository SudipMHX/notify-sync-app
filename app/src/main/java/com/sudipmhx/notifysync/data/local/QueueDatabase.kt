package com.sudipmhx.notifysync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QueueEntity::class], version = 1, exportSchema = false)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: QueueDatabase? = null

        fun getDatabase(context: Context): QueueDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QueueDatabase::class.java,
                    "notify_sync_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
