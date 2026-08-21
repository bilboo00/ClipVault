package com.clipvault.manager.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipvault.manager.data.local.ClipDatabase
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.dao.CollectionDao
import com.clipvault.manager.data.local.dao.SnippetDao
import com.clipvault.manager.data.local.dao.TagDao
import com.clipvault.manager.data.local.dao.UrlPreviewDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ClipDatabase =
        Room.databaseBuilder(ctx, ClipDatabase::class.java, "clipboard.db")
            .addMigrations(*ClipDatabase.MIGRATIONS)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // FTS4's external-content index can drift if a write
                    // bypassed the triggers (e.g. a manual UPDATE outside
                    // Room). The integrity-check is cheap, so run it on
                    // every open and log failures for visibility.
                    ClipDatabase.checkFtsIntegrity(db)
                }
            })
            .build()

    @Provides
    fun provideClipDao(db: ClipDatabase): ClipDao = db.clipDao()

    @Provides
    fun provideSnippetDao(db: ClipDatabase): SnippetDao = db.snippetDao()

    @Provides
    fun provideUrlPreviewDao(db: ClipDatabase): UrlPreviewDao = db.urlPreviewDao()

    @Provides
    fun provideTagDao(db: ClipDatabase): TagDao = db.tagDao()

    @Provides
    fun provideCollectionDao(db: ClipDatabase): CollectionDao = db.collectionDao()
}