package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalProjectDao {
    @Query("SELECT * FROM local_projects WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeProjectsForUser(userId: String): Flow<List<LocalProjectEntity>>

    @Query("SELECT * FROM local_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): LocalProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: LocalProjectEntity)

    @Query("UPDATE local_projects SET isSyncedToCloud = :synced WHERE id = :id")
    suspend fun markSynced(id: String, synced: Boolean)

    @Query("DELETE FROM local_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}
