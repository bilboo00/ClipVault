package com.clipvault.manager.domain.model

import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType

data class Clip(
    val id: Long,
    val content: String,
    val type: ClipType,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val createdAt: Long,
    val sourceLabel: String?,
    val imageUri: String? = null,
    val notes: String? = null,
    val expiresAt: Long? = null,
    val useLimit: Int? = null,
    val useCount: Int = 0,
    val isLocked: Boolean = false
) {
    val preview: String
        get() = if (content.length <= 120) content else content.take(120) + "…"

    val hasNotes: Boolean
        get() = !notes.isNullOrBlank()

    val hasExpiration: Boolean
        get() = expiresAt != null

    val hasUseLimit: Boolean
        get() = useLimit != null

    companion object {
        fun fromEntity(e: ClipEntity) = Clip(
            id = e.id,
            content = e.content,
            type = e.type,
            isPinned = e.isPinned,
            isFavorite = e.isFavorite,
            createdAt = e.createdAt,
            sourceLabel = e.sourceLabel,
            imageUri = e.imageUri,
            notes = e.notes,
            expiresAt = e.expiresAt,
            useLimit = e.useLimit,
            useCount = e.useCount,
            isLocked = e.isLocked
        )
    }
}