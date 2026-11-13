package com.clipvault.manager

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Shares one [TestDispatcher] between `Dispatchers.Main` and `runTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

fun clipEntity(
    id: Long = 1,
    content: String = "hello world",
    type: ClipType = ClipType.TEXT,
    isPinned: Boolean = false,
    isFavorite: Boolean = false,
    createdAt: Long = 1_700_000_000_000L,
    imageUri: String? = null,
    notes: String? = null,
    expiresAt: Long? = null,
    useLimit: Int? = null,
    useCount: Int = 0,
    isLocked: Boolean = false
) = ClipEntity(
    id = id,
    content = content,
    type = type,
    isPinned = isPinned,
    isFavorite = isFavorite,
    createdAt = createdAt,
    sourceLabel = null,
    sortOrder = 0,
    imageUri = imageUri,
    notes = notes,
    expiresAt = expiresAt,
    useLimit = useLimit,
    useCount = useCount,
    isLocked = isLocked
)

/** In-memory PagingSource returning one fixed page. */
class FakeClipPagingSource(
    private val data: List<ClipEntity>
) : PagingSource<Int, ClipEntity>() {
    override fun getRefreshKey(state: PagingState<Int, ClipEntity>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ClipEntity> =
        LoadResult.Page(data = data, prevKey = null, nextKey = null)
}

private class NoopListUpdateCallback : ListUpdateCallback {
    override fun onInserted(position: Int, count: Int) = Unit
    override fun onRemoved(position: Int, count: Int) = Unit
    override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
    override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
}

private val clipDiffCallback = object : DiffUtil.ItemCallback<Clip>() {
    override fun areItemsTheSame(oldItem: Clip, newItem: Clip): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Clip, newItem: Clip): Boolean =
        oldItem == newItem
}

/** Materializes PagingData in JVM tests (needs recyclerview test dep). */
@OptIn(ExperimentalCoroutinesApi::class)
fun testDiffer(dispatcher: TestDispatcher = StandardTestDispatcher()) =
    AsyncPagingDataDiffer(
        diffCallback = clipDiffCallback,
        updateCallback = NoopListUpdateCallback(),
        mainDispatcher = dispatcher,
        workerDispatcher = dispatcher
    )
