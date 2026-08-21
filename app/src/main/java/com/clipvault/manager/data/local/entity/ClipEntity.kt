package com.clipvault.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ClipType {
    TEXT, URL, EMAIL, PHONE, CODE, NUMBER,
    COLOR_HEX, IBAN, UUID, JSON, WALLET_ADDRESS, IP, IMAGE, OTP
}

@Entity(
    tableName = "clips",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["sortOrder"]),
        Index(value = ["isPinned", "sortOrder", "createdAt"]),
        Index(value = ["expiresAt", "isPinned"]),
        Index(value = ["useLimit", "isPinned"]),
        Index(value = ["isPinned", "createdAt"])
    ]
)
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: ClipType = ClipType.TEXT,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceLabel: String? = null,
    /** Position among pinned items (lower = higher in list). 0 for unpinned. */
    val sortOrder: Int = 0,
    /** Local file path for image clips. Null for text clips. */
    val imageUri: String? = null,
    /** User-added notes (full markdown supported). Null if no notes. */
    val notes: String? = null,
    /** Timestamp when this clip should auto-delete. Null = no expiration. */
    val expiresAt: Long? = null,
    /** Max number of pastes before auto-delete. Null = unlimited. */
    val useLimit: Int? = null,
    /** How many times this clip has been pasted/copied. */
    val useCount: Int = 0,
    /** If true, requires biometric auth to view content. */
    val isLocked: Boolean = false
)