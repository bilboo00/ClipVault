package com.clipvault.manager.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("PasteQueueStorage", "scope failure", e)
        }
    )

    val queue: StateFlow<PasteQueueData> = dataStore.data.map { prefs ->
        val raw = prefs[keyClipIds].orEmpty()
        val ids = if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
        PasteQueueData(clipIds = ids, currentIndex = prefs[keyCurrentIndex] ?: 0)
    }.flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PasteQueueData())

    suspend fun setQueue(value: PasteQueueData) = withContext(Dispatchers.IO) {
        // Cap at MAX_QUEUE_SIZE so the persisted blob stays bounded; oldest
        // entries are dropped (FIFO).
        val capped = if (value.clipIds.size > MAX_QUEUE_SIZE) {
            value.copy(clipIds = value.clipIds.takeLast(MAX_QUEUE_SIZE))
        } else value
        dataStore.edit { prefs ->
            prefs[keyClipIds] = capped.clipIds.joinToString(",")
            prefs[keyCurrentIndex] = capped.currentIndex
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs.remove(keyClipIds)
            prefs.remove(keyCurrentIndex)
        }
    }

    private companion object {
        const val MAX_QUEUE_SIZE = 200
    }
}