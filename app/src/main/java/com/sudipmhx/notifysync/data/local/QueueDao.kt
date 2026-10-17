package com.sudipmhx.notifysync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QueueDao {
    @Insert
    suspend fun insert(entity: QueueEntity)

    @Insert
    suspend fun insertAll(entities: List<QueueEntity>)

    @Query("SELECT * FROM sync_queue WHERE synced = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<QueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("UPDATE sync_queue SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM sync_queue WHERE synced = 1")
    suspend fun deleteSynced()
}
