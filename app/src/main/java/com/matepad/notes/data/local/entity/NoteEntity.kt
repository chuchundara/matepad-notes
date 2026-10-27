package com.matepad.notes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("folder_id"),
        Index("updated_at")
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "preview_path") val previewPath: String? = null,
    @ColumnInfo(name = "viewport_zoom") val viewportZoom: Float = 1.0f,
    @ColumnInfo(name = "viewport_offset_x") val viewportOffsetX: Float = 0.0f,
    @ColumnInfo(name = "viewport_offset_y") val viewportOffsetY: Float = 0.0f,
    @ColumnInfo(name = "grid_style") val gridStyle: String = "DOTS"
)
