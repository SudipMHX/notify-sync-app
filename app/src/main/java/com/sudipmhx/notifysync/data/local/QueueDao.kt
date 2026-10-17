package com.sudipmhx.notifysync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QueueDao {
    @Insert
    suspend fun insert(entity: QueueEntity)

    @Query("SELECT * FROM sync_queue WHERE synced = 0")
    suspend fun getUnsynced(): List<QueueEntity>

    @Query("UPDATE sync_queue SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("DELETE FROM sync_queue WHERE synced = 1")
    suspend fun deleteSynced()
}
