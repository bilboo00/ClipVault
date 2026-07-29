package com.clipvault.manager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#4F46E5",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "clip_tags",
    primaryKeys = ["clipId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clipId"]),
        Index(value = ["tagId"])
    ]
)
data class ClipTagCrossRef(
    val clipId: Long,
    val tagId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "clip_collections",
    primaryKeys = ["clipId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = ClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clipId"]),
        Index(value = ["collectionId"])
    ]
)
data class ClipCollectionCrossRef(
    val clipId: Long,
    val collectionId: Long,
    val addedAt: Long = System.currentTimeMillis()
)