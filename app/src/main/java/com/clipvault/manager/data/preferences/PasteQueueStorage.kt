package com.clipvault.manager.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pasteQueueDataStore by preferencesDataStore(name = "paste_queue")

data class PasteQueueData(
    val clipIds: List<Long> = emptyList(),
    val currentIndex: Int = 0
)

@Singleton
class PasteQueueStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.pasteQueueDataStore

    private val keyClipIds = stringPreferencesKey("clip_ids")
    private val keyCurrentIndex = intPreferencesKey("current_index")

    val queue: Flow<PasteQueueData> = dataStore.data.map { prefs ->
        val raw = prefs[keyClipIds].orEmpty()
        val ids = if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
        PasteQueueData(clipIds = ids, currentIndex = prefs[keyCurrentIndex] ?: 0)
    }.flowOn(Dispatchers.IO)

    suspend fun setQueue(ids: List<Long>, currentIndex: Int = 0) = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs[keyClipIds] = ids.joinToString(",")
            prefs[keyCurrentIndex] = currentIndex
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs.remove(keyClipIds)
            prefs.remove(keyCurrentIndex)
        }
    }
}