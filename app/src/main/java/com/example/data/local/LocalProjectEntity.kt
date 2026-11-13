package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_projects")
data class LocalProjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val aspectRatio: String,
    val resolution: String,
    val fps: Int,
    val durationMs: Long,
    val layersJson: String,
    val templateOriginId: String? = null,
    val isSyncedToCloud: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
