package com.clipvault.manager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "clips_fts")
@Fts4(contentEntity = ClipEntity::class)
data class ClipFtsEntity(
    @ColumnInfo(name = "content")
    val content: String
)
