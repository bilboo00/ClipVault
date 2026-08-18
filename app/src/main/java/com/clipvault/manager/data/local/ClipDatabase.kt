package com.clipvault.manager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.dao.CollectionDao
import com.clipvault.manager.data.local.dao.SnippetDao
import com.clipvault.manager.data.local.dao.TagDao
import com.clipvault.manager.data.local.dao.UrlPreviewDao
import com.clipvault.manager.data.local.entity.ClipCollectionCrossRef
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipFtsEntity
import com.clipvault.manager.data.local.entity.ClipTagCrossRef
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.local.entity.SnippetEntity
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.data.local.entity.UrlPreviewEntity

class ClipTypeConverter {
    @TypeConverter
    fun toClipType(value: String): ClipType = ClipType.valueOf(value)

    @TypeConverter
    fun fromClipType(type: ClipType): String = type.name
}

@Database(
    entities = [
        ClipEntity::class,
        ClipFtsEntity::class,
        SnippetEntity::class,
        UrlPreviewEntity::class,
        TagEntity::class,
        ClipTagCrossRef::class,
        CollectionEntity::class,
        ClipCollectionCrossRef::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(ClipTypeConverter::class)
abstract class ClipDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao
    abstract fun snippetDao(): SnippetDao
    abstract fun urlPreviewDao(): UrlPreviewDao
    abstract fun tagDao(): TagDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS snippets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastUsedAt INTEGER,
                        useCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS url_previews (
                        url TEXT NOT NULL PRIMARY KEY,
                        title TEXT,
                        fetchedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN imageUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN notes TEXT DEFAULT NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        color TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clip_tags (
                        clipId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(clipId, tagId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_tags_clipId ON clip_tags(clipId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_tags_tagId ON clip_tags(tagId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clip_collections (
                        clipId INTEGER NOT NULL,
                        collectionId INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(clipId, collectionId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_collections_clipId ON clip_collections(clipId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_collections_collectionId ON clip_collections(collectionId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN expiresAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE clips ADD COLUMN useLimit INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE clips ADD COLUMN useCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clips ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clips_expiresAt ON clips(expiresAt)")
                // Rebuild url_previews to add new columns — Room can't ALTER a primary key easily,
                // so we drop and recreate (cache is non-critical, will be repopulated on demand).
                db.execSQL("""
                    CREATE TABLE url_previews_new (
                        url TEXT NOT NULL PRIMARY KEY,
                        title TEXT,
                        description TEXT,
                        imageUrl TEXT,
                        siteName TEXT,
                        fetchedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO url_previews_new (url, title, fetchedAt) SELECT url, title, fetchedAt FROM url_previews")
                db.execSQL("DROP TABLE url_previews")
                db.execSQL("ALTER TABLE url_previews_new RENAME TO url_previews")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // FTS4 external-content index over clips.content.
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `clips_fts` USING FTS4(content=`clips`, content, tokenize=simple)"
                )
                db.execSQL("INSERT INTO `clips_fts`(`clips_fts`) VALUES('rebuild')")
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `clips_fts_ai` AFTER INSERT ON `clips` " +
                        "BEGIN INSERT INTO `clips_fts`(`rowid`, `content`) VALUES (new.`rowid`, new.`content`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `clips_fts_ad` AFTER DELETE ON `clips` " +
                        "BEGIN INSERT INTO `clips_fts`(`clips_fts`, `rowid`, `content`) " +
                        "VALUES ('delete', old.`rowid`, old.`content`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `clips_fts_au` AFTER UPDATE ON `clips` " +
                        "BEGIN INSERT INTO `clips_fts`(`clips_fts`, `rowid`, `content`) " +
                        "VALUES ('delete', old.`rowid`, old.`content`); " +
                        "INSERT INTO `clips_fts`(`rowid`, `content`) VALUES (new.`rowid`, new.`content`); END"
                )
            }
        }

        /**
         * Every migration, in order. Both the app database builder
         * ([com.clipvault.manager.di.DatabaseModule]) and the widget's own
         * builder must stay on this list, otherwise widget launches crash on
         * schema changes.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )
    }
}