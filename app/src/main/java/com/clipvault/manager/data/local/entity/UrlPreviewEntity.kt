package com.clipvault.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached URL metadata (title, description, image, site name from Open Graph). */
@Entity(
    tableName = "url_previews",
    primaryKeys = ["url"],
    indices = [Index(value = ["fetchedAt"])]
)
data class UrlPreviewEntity(
    val url: String,
    val title: String?,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)