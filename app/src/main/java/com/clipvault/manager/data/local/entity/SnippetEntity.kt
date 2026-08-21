package com.clipvault.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Snippets: user-defined reusable text inserts (email signatures, addresses, replies). */
@Entity(
    tableName = "snippets",
    indices = [Index(value = ["lastUsedAt"])]
)
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val useCount: Int = 0
)