package com.clipvault.manager.data.repository

import android.content.Context
import android.database.SQLException
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.dao.DuplicateGroup
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import com.clipvault.manager.widget.ClipboardGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

class ClipboardDbException(cause: Throwable) :
    RuntimeException("Database operation failed", cause)

@Singleton
class ClipboardRepository @Inject constructor(
    private val dao: ClipDao,
    private val settings: SettingsManager,
    @ApplicationContext private val context: Context
) {

    /**
     * Best-effort, on-demand widget refresh so a new/edited clip shows up in
     * the home-screen widget without waiting for the 30-minute update cycle.
     * Debounced so a burst of mutations (multi-select delete, reorder, lock
     * toggles) coalesces into a single render instead of hammering the widget's
     * separate Room connection on every change. Fire-and-forget; failures must
     * never surface to the caller.
     */
    private var widgetRefreshJob: Job? = null
    private fun refreshWidget() {
        synchronized(this) {
            widgetRefreshJob?.cancel()
            widgetRefreshJob = scope.launch {
                delay(WIDGET_REFRESH_DEBOUNCE_MS)
                runCatching { ClipboardGlanceWidget().updateAll(context) }
            }
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("ClipboardRepository", "scope failure", e)
        }
    )

    private suspend inline fun <T> dbOp(block: suspend () -> T): T = try {
        block()
    } catch (e: SQLException) {
        Log.e(TAG, "DB operation failed", e)
        throw ClipboardDbException(e)
    }

    // Contents/paths the user explicitly deleted. The capture services re-verify
    // the current clipboard against the DB (delete → re-copy must re-save) and
    // would otherwise silently re-add a deleted clip that is still sitting on
    // the clipboard. Suppressed entries survive process restarts via a small
    // file and are cleared once the clipboard moves on to different content
    // (see [clearDeleteSuppressions]).
    //
    // Bounded LinkedHashSet so memory can't grow without bound even after a
    // long session of deletes — oldest entries drop off when MAX_SUPPRESSED is
    // reached (insertion-order, which Kotlin's LinkedHashSet exposes here since
    // the 3-arg (accessOrder) constructor isn't visible through the stdlib
    // typealias).
    @Suppress("UNCHECKED_CAST")
    private val suppressedContents: MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<String>(MAX_SUPPRESSED, 0.75f)
    ) as MutableSet<String>
    @Suppress("UNCHECKED_CAST")
    private val suppressedImages: MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.LinkedHashSet<String>(MAX_SUPPRESSED, 0.75f)
    ) as MutableSet<String>
    private val suppressionFile: File by lazy { File(context.filesDir, "deleted_clips.json") }

    init {
        loadDeleteSuppressions()
    }

    /** True if [content] was explicitly deleted while still on the clipboard. */
    fun isContentSuppressed(content: String): Boolean =
        synchronized(this) { content in suppressedContents }

    /** True if [imagePath] was explicitly deleted while still on the clipboard. */
    fun isImageSuppressed(imagePath: String): Boolean =
        synchronized(this) { imagePath in suppressedImages }

    /**
     * The clipboard now holds different content, so nothing the user deleted can
     * be re-added by a stale verify pass anymore — all suppressions are moot.
     */
    fun clearDeleteSuppressions() {
        synchronized(this) {
            if (suppressedContents.isEmpty() && suppressedImages.isEmpty()) return
            suppressedContents.clear()
            suppressedImages.clear()
            persistSuppressions()
        }
    }

    private fun suppressEntities(entities: List<ClipEntity>) {
        if (entities.isEmpty()) return
        synchronized(this) {
            entities.forEach { entity ->
                if (entity.type == ClipType.IMAGE) {
                    addSuppressed(suppressedImages, entity.imageUri.orEmpty())
                } else {
                    addSuppressed(suppressedContents, entity.content)
                }
            }
            persistSuppressions()
        }
    }

    private fun loadDeleteSuppressions() {
        runCatching {
            val file = suppressionFile
            if (!file.exists()) return@runCatching
            val json = JSONObject(file.readText())
            json.optJSONArray("contents")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotEmpty() }?.let {
                        addSuppressed(suppressedContents, it)
                    }
                }
            }
            json.optJSONArray("images")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotEmpty() }?.let {
                        addSuppressed(suppressedImages, it)
                    }
                }
            }
        }
    }

    private var persistSuppressionsJob: Job? = null
    private fun persistSuppressions() {
        // Debounced: a bulk delete (500 clips) used to launch 500 writeText
        // coroutines hammering the same file; the 250 ms window coalesces a
        // burst into a single write at the tail end.
        synchronized(this) {
            persistSuppressionsJob?.cancel()
            persistSuppressionsJob = scope.launch {
                delay(SUPPRESSIONS_DEBOUNCE_MS)
                runCatching {
                    // Re-snapshot under the lock so the write reflects the
                    // current sets, not the ones present when the debounce
                    // started.
                    val snapshot = synchronized(this@ClipboardRepository) {
                        JSONArray().apply { suppressedContents.forEach(::put) } to
                            JSONArray().apply { suppressedImages.forEach(::put) }
                    }
                    val (contents, images) = snapshot
                    suppressionFile.writeText(
                        JSONObject().put("contents", contents).put("images", images).toString()
                    )
                }
            }
        }
    }

    private fun addSuppressed(set: MutableSet<String>, value: String) {
        if (value.isEmpty()) return
        set.add(value)
        // Evict LRU until back under the cap.
        while (set.size > MAX_SUPPRESSED) {
            val it = set.iterator()
            if (!it.hasNext()) break
            it.next()
            it.remove()
        }
    }
    fun observeAll(): Flow<List<Clip>> =
        dao.observeAll().map { list -> list.map(Clip.Companion::fromEntity) }

    fun observeByType(type: ClipType): Flow<List<Clip>> =
        dao.observeByType(type.name).map { list -> list.map(Clip.Companion::fromEntity) }

    /**
     * Pinned clips only — bounded by the size of the pinned tray (typically a
     * handful of rows), so a plain Flow is fine without paging.
     */
    fun observePinned(): Flow<List<Clip>> =
        dao.observePinned().map { list -> list.map(Clip.Companion::fromEntity) }

    fun search(query: String): Flow<List<Clip>> =
        dao.search(query.escapeLikeQuery()).map { list -> list.map(Clip.Companion::fromEntity) }

    /**
     * Paged wrapper over the full history. The PagingSource replaces the old
     * `observeAll()` Flow so we never hold the entire history in memory; the
     * ViewModel pipes this through `cachedIn(viewModelScope)` so configuration
     * changes don't drop the loaded window.
     */
    fun pagingAll(): Flow<PagingData<Clip>> = Pager(
        config = PagingConfig(pageSize = 50, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = { dao.pagingSourceAll() }
    ).flow.map { paging -> paging.map(Clip.Companion::fromEntity) }

    fun pagingByType(type: ClipType): Flow<PagingData<Clip>> = Pager(
        config = PagingConfig(pageSize = 50, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = { dao.pagingSourceByType(type.name) }
    ).flow.map { paging -> paging.map(Clip.Companion::fromEntity) }

    /**
     * Paged FTS search. Reuses the same prefix-phrase escaping as [searchFts]
     * so user input never reaches the FTS syntax parser unguarded; an empty
     * query returns a single empty PagingData rather than a wildcard scan.
     */
    fun pagingSearch(raw: String): Flow<PagingData<Clip>> {
        val tokens = raw.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace("\"", "\"\"") }
        if (tokens.isEmpty()) return flowOf(PagingData.empty())
        val ftsQuery = tokens.joinToString(" ") { "\"$it\"*" }
        return Pager(
            config = PagingConfig(pageSize = 50, prefetchDistance = 10, enablePlaceholders = false),
            pagingSourceFactory = { dao.pagingSourceSearchFts(ftsQuery) }
        ).flow.map { paging -> paging.map(Clip.Companion::fromEntity) }
    }

    /**
     * Full-text search over the FTS4 index. The query is escaped into a quoted
     * prefix-phrase expression so user input never reaches FTS syntax parser
     * unguarded; malformed-input exceptions are swallowed as empty results.
     */
    fun searchFts(raw: String): Flow<List<Clip>> {
        val tokens = raw.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace("\"", "\"\"") }
        if (tokens.isEmpty()) return emptyFlow()
        val ftsQuery = tokens.joinToString(" ") { "\"$it\"*" }
        val sql = SimpleSQLiteQuery(
            "SELECT clips.* FROM clips " +
                "JOIN clips_fts ON clips_fts.rowid = clips.id " +
                "WHERE clips_fts MATCH ? " +
                "ORDER BY clips.isPinned DESC, clips.createdAt DESC LIMIT 100",
            arrayOf(ftsQuery)
        )
        return flow {
            emitAll(
                dao.searchFts(sql).map { list -> list.map(Clip.Companion::fromEntity) }
            )
        }.catch { emit(emptyList()) }
    }

    fun observeById(id: Long): Flow<Clip?> =
        dao.observeById(id).map { entity -> entity?.let(Clip.Companion::fromEntity) }

    /** True if a clip with this exact content is still in history. */
    suspend fun contentExists(content: String): Boolean = dao.countByContent(content) > 0

    /** True if a clip still references this stored image path. */
    suspend fun imageExists(path: String): Boolean = dao.countByImageUri(path) > 0

    /**
     * Delete stored image files that no clip references anymore, and evict
     * the oldest non-pinned clip images until total disk usage fits under
     * the user's [SettingsManager.maxImageBytes] quota. [minAgeMs] keeps
     * recently-written files around so a quick undo of a delete doesn't
     * end up with a broken image. Best-effort; any per-file failure is
     * swallowed because maintenance must never crash the app.
     */
    suspend fun cleanupOrphanedImages(filesDir: java.io.File, minAgeMs: Long) {
        val referenced = dao.getAllImagePaths().toSet()
        val now = System.currentTimeMillis()
        val dir = imagesDir(filesDir)
        val orphans = mutableListOf<File>()
        dir.listFiles { f ->
            f.isFile && f.name.startsWith("clip_") && f.name.endsWith(".png")
        }?.forEach { file ->
            if (file.absolutePath !in referenced && now - file.lastModified() > minAgeMs) {
                orphans += file
            }
        }
        orphans.forEach { runCatching { it.delete() } }
        try {
            enforceImageQuota(filesDir)
        } catch (_: Exception) {
            // Quota enforcement is best-effort — never fail the orphan sweep.
        }
    }

    /**
     * If the total size of `filesDir/images/` exceeds the user's
     * [SettingsManager.maxImageBytes] quota, evict the oldest
     * non-pinned clip images (LRU by file mtime — the `clip_<ts>_<seq>`
     * naming scheme is timestamp-sorted) until under quota. Pinned
     * clips are protected and never deleted. After deleting a file the
     * clip's `imageUri` is nulled so the in-app renderer falls back to
     * a placeholder rather than serving a dangling FileProvider URI.
     */
    suspend fun enforceImageQuota(filesDir: java.io.File) {
        val quota = settings.maxImageBytes.first().toLong()
        val dir = imagesDir(filesDir)
        if (!dir.exists()) return
        val pinned = dao.getPinnedImagePaths().toSet()
        val candidates = dir.listFiles { f ->
            f.isFile && f.name.startsWith("clip_") && f.name.endsWith(".png")
        }?.toList()?.sortedBy { it.lastModified() } ?: return
        var total = candidates.sumOf { it.length() }
        if (total <= quota) return
        for (file in candidates) {
            if (total <= quota) break
            if (file.absolutePath in pinned) continue
            if (runCatching { file.delete() }.getOrDefault(false)) {
                runCatching { dao.clearImageUri(file.absolutePath) }
                total -= file.length()
            }
        }
    }

    /**
     * One-time migration for the FileProvider hardening: clips saved before the
     * `filesDir/images/` layout stored images directly in [filesDir]. Move any
     * still-referenced legacy files into the images subdirectory and rewrite the
     * clip rows so both sharing and the narrowed FileProvider keep working.
     * Idempotent: once a file is in `filesDir/images/`, the listing in
     * [filesDir] returns nothing, so repeated calls are a no-op. Safe to run
     * on every startup or as part of the periodic image sweep.
     */
    suspend fun relocateLegacyImages(filesDir: java.io.File) {
        val target = imagesDir(filesDir)
        if (!target.exists() && !target.mkdirs()) return
        filesDir.listFiles { f ->
            f.isFile && f.name.startsWith("clip_") && f.name.endsWith(".png")
        }?.forEach { file ->
            val dest = File(target, file.name)
            // Rename is atomic on the same filesystem; if a destination
            // already exists (race), skip rather than clobber.
            if (!dest.exists() && file.renameTo(dest)) {
                dao.updateImagePath(file.absolutePath, dest.absolutePath)
            }
        }
    }

    private fun imagesDir(filesDir: java.io.File): java.io.File =
        java.io.File(filesDir, "images")

    /**
     * Delete tag/collection cross-ref rows that point at clips, tags, or
     * collections that no longer exist (delete paths have no FK cascades).
     */
    suspend fun cleanupOrphanedRefs() {
        dao.purgeOrphanedCrossRefs()
    }

    suspend fun saveIfNew(content: String, sourceLabel: String? = null): Long? = dbOp {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return@dbOp null
        // The user explicitly deleted this content while it's still on the
        // clipboard — don't let a capture-loop re-verify resurrect it.
        if (isContentSuppressed(trimmed)) return@dbOp null
        if (dao.countByContent(trimmed) > 0) return@dbOp null
        val type = ClipClassifier.classify(trimmed)
        val entity = ClipEntity(
            content = trimmed,
            type = type,
            sourceLabel = sourceLabel,
            // One-time codes self-destruct after a short window so a leaked
            // verification code doesn't linger in history.
            expiresAt = if (type == ClipType.OTP)
                System.currentTimeMillis() + OTP_TTL_MS
            else null
        )
        val id = dao.insert(entity).takeIf { it != -1L }
        if (id != null) refreshWidget()
        id
    }

    suspend fun saveImage(imagePath: String, sourceLabel: String? = null): Long? = dbOp {
        if (isImageSuppressed(imagePath)) return@dbOp null
        val entity = ClipEntity(
            content = "[Image]",
            type = ClipType.IMAGE,
            sourceLabel = sourceLabel,
            imageUri = imagePath
        )
        val id = dao.insert(entity).takeIf { it != -1L }
        if (id != null) refreshWidget()
        id
    }

    suspend fun togglePin(id: Long, pinned: Boolean) = dbOp {
        val newSortOrder = if (pinned) dao.minPinnedSortOrder() - 1 else 0
        dao.setPinned(id, pinned)
        dao.setSortOrder(id, newSortOrder)
        refreshWidget()
    }

    suspend fun bulkSetPinned(ids: List<Long>, pinned: Boolean): Int = dbOp {
        val updated = dao.setPinnedBulk(ids, pinned)
        if (pinned) {
            // Give each newly pinned clip a distinct sortOrder so the batch keeps
            // a stable order instead of all sharing one identical value.
            var next = dao.minPinnedSortOrder() - 1
            ids.forEach { id -> dao.setSortOrder(id, next--) }
        } else {
            dao.setSortOrderBulk(ids, 0)
        }
        updated
    }

    suspend fun reorderPinned(orderedIds: List<Long>) = dbOp {
        dao.reorderPinnedWithOrders(orderedIds.mapIndexed { index, id -> id to index }.toMap())
    }

    suspend fun bulkDelete(ids: List<Long>): Int = dbOp {
        suppressEntities(dao.getByIds(ids))
        val deleted = dao.deleteByIds(ids)
        if (deleted > 0) refreshWidget()
        deleted
    }
    suspend fun getByIds(ids: List<Long>): List<ClipEntity> = dao.getByIds(ids)
    suspend fun toggleFavorite(id: Long, favorite: Boolean) = dbOp { dao.setFavorite(id, favorite) }
    suspend fun updateText(id: Long, content: String) = dbOp {
        dao.getById(id)?.let { entity ->
            val trimmed = content.trim()
            // Reclassify on edit so badges don't stay stale (URL → text, etc.),
            // but never flip an image clip away from IMAGE via text transforms.
            val type = if (entity.type == ClipType.IMAGE) ClipType.IMAGE
            else ClipClassifier.classify(trimmed)
            dao.update(entity.copy(content = trimmed, type = type))
            refreshWidget()
        }
    }

    suspend fun setNotes(id: Long, notes: String?) = dbOp {
        dao.setNotes(id, notes?.trim()?.takeIf { it.isNotEmpty() })
        refreshWidget()
    }

    suspend fun setExpiration(id: Long, expiresAt: Long?) = dbOp {
        dao.setExpiresAt(id, expiresAt)
        refreshWidget()
    }

    suspend fun setUseLimit(id: Long, useLimit: Int?) = dbOp { dao.setUseLimit(id, useLimit) }

    suspend fun incrementUseCount(id: Long) = dbOp { dao.incrementUseCount(id) }

    suspend fun setLocked(id: Long, locked: Boolean) = dbOp {
        dao.setLocked(id, locked)
        refreshWidget()
    }

    suspend fun pruneExpired(): Int = dbOp {
        val now = System.currentTimeMillis()
        suppressEntities(dao.getExpiredBefore(now))
        dao.pruneExpired(now)
    }

    suspend fun pruneExhausted(): Int = dbOp {
        suppressEntities(dao.getExhausted())
        dao.pruneExhausted()
    }

    suspend fun findDuplicatesOf(content: String): List<Clip> =
        dao.findDuplicatesOf(content).map(Clip.Companion::fromEntity)

    suspend fun findDuplicateGroups(): List<DuplicateGroup> = dao.findDuplicateGroups()

    /**
     * Merge all duplicates of [content] into [keepId]: use counts are folded,
     * tag/collection crossrefs are re-pointed (skipping ones the keeper already
     * has), notes are preserved if the keeper has none, and the duplicate rows
     * are deleted. Pinned duplicates are never deleted.
     *
     * @return the number of clips deleted.
     */
    suspend fun mergeDuplicateGroup(keepId: Long, content: String): Int = dbOp {
        val duplicates = dao.findDuplicatesExcluding(keepId, content).filter { !it.isPinned }
        if (duplicates.isEmpty()) return@dbOp 0
        val ids = duplicates.map { it.id }
        val keeper = dao.getById(keepId)
        val notesToKeep = if (keeper?.notes.isNullOrBlank()) {
            duplicates.firstOrNull { !it.notes.isNullOrBlank() }?.notes
        } else {
            null
        }
        dao.mergeDuplicateGroup(keepId, ids, notesToKeep)
        refreshWidget()
        ids.size
    }

    suspend fun delete(id: Long) = dbOp {
        dao.getById(id)?.let { suppressEntities(listOf(it)) }
        dao.deleteById(id)
        refreshWidget()
    }
    suspend fun deleteAll() = dbOp {
        suppressEntities(dao.getAll())
        dao.deleteAll()
        refreshWidget()
    }
    suspend fun deleteAllUnpinned(): Int = dbOp {
        suppressEntities(dao.getUnpinned())
        val deleted = dao.deleteUnpinned()
        if (deleted > 0) refreshWidget()
        deleted
    }
    suspend fun pruneOlderThan(cutoff: Long) = dbOp {
        suppressEntities(dao.getOlderThan(cutoff))
        dao.deleteOlderThan(cutoff)
        refreshWidget()
    }
    suspend fun insertForUndo(entity: ClipEntity) = dbOp {
        dao.restore(entity)
        refreshWidget()
    }
    suspend fun restoreBulk(entities: List<ClipEntity>) = dbOp {
        dao.restoreAll(entities)
        refreshWidget()
    }
    suspend fun count(): Int = dao.count()
    suspend fun getAll(): List<Clip> = dao.getAll().map(Clip.Companion::fromEntity)
    suspend fun getAllEntities(): List<ClipEntity> = dao.getAll()
    suspend fun insertForImport(entity: ClipEntity): Long = dbOp {
        val id = dao.insert(entity)
        if (id != -1L) refreshWidget()
        id
    }
    suspend fun countSince(since: Long): Int = dao.countSince(since)
    suspend fun countByType(): List<Pair<ClipType, Int>> =
        dao.countByType().mapNotNull { tc ->
            runCatching { ClipType.valueOf(tc.type) to tc.cnt }.getOrNull()
        }
    suspend fun totalContentBytes(): Long = dao.totalContentLength() ?: 0L

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeCountSince(since: Long): Flow<Int> = dao.observeCountSince(since)

    fun observeCountByType(): Flow<List<Pair<ClipType, Int>>> =
        dao.observeCountByType().map { rows ->
            rows.mapNotNull { tc ->
                runCatching { ClipType.valueOf(tc.type) to tc.cnt }.getOrNull()
            }
        }

    fun observeTotalContentBytes(): Flow<Long> = dao.observeTotalContentLength().map { it ?: 0L }

    /** Escape SQL LIKE wildcards so user queries match literally. */
    private fun String.escapeLikeQuery(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val TAG = "ClipboardRepository"

        /** One-time codes are removed 10 minutes after capture. */
        const val OTP_TTL_MS = 10 * 60 * 1000L

        /** Coalescing window for widget refreshes (see [refreshWidget]). */
        const val WIDGET_REFRESH_DEBOUNCE_MS = 1_500L

        /** Coalescing window for suppression-list writes (see [persistSuppressions]). */
        const val SUPPRESSIONS_DEBOUNCE_MS = 250L

        /** Cap on the suppression sets so memory is bounded. */
        const val MAX_SUPPRESSED = 500
    }
}