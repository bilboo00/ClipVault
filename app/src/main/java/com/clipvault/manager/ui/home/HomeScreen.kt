package com.clipvault.manager.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.sensor.ShakeDetector
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import com.clipvault.manager.util.ClipUtils
import com.clipvault.manager.util.ImageCopier
import com.clipvault.manager.ui.components.AnimatedToggleIcon
import com.clipvault.manager.ui.components.CopyHeroOverlay
import com.clipvault.manager.ui.components.CopyPill
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.InlinePreview
import com.clipvault.manager.ui.components.QuickActionPill
import com.clipvault.manager.ui.components.MultiSelectActionBar
import com.clipvault.manager.ui.components.MultiSelectClipRow
import com.clipvault.manager.ui.components.MultiSelectTopBar
import com.clipvault.manager.ui.components.QueueSheet
import com.clipvault.manager.ui.components.SaveFab
import com.clipvault.manager.ui.components.StackedSnackbarHost
import com.clipvault.manager.ui.components.SwipeAction
import com.clipvault.manager.ui.components.SwipeableRow
import com.clipvault.manager.ui.components.TypeFilterSheet
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.components.typeIcon
import com.clipvault.manager.ui.components.rememberCopyHeroState
import com.clipvault.manager.ui.components.rememberStackedSnackbarHostState
import com.clipvault.manager.ui.theme.Spacing
import com.clipvault.manager.ui.theme.Motion
import com.clipvault.manager.util.rememberRelativeTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Snapshot all topbar / scaffold fields once. Reading `state.X` from
    // inside the Scaffold's `topBar` lambda (and other slot lambdas) still
    // counts as a read of the delegated `state` — the Scaffold lambda runs
    // every time the parent recomposes. Capturing once at the top means
    // those slot lambdas receive plain vals and don't re-subscribe.
    val multiSelectMode = state.multiSelectMode
    val selectedIds = state.selectedIds
    val loadedCount = state.loadedCount
    val totalCount = state.totalCount
    val monitoringActive = state.monitoringActive
    val queueItems = state.queueItems
    val savingNow = state.savingNow
    val viewMode = state.viewMode
    val activeFilter = state.activeFilter
    val pinnedClips = state.pinnedClips
    val favoriteCount = state.favoriteCount
    val justCopiedForId = state.justCopiedForId
    val context = LocalContext.current
    val snackbarHostState = rememberStackedSnackbarHostState()
    val haptics = rememberHaptics()
    val hero = rememberCopyHeroState()
    val listState = rememberLazyListState()
    val topBarBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val urlTitles = viewModel.titleMap
    var showClearDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showTypeFilterSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }

    val lazyClips = viewModel.clipsFlow.collectAsLazyPagingItems()
    LaunchedEffect(lazyClips.itemCount) {
        viewModel.setLoadedIds(
            lazyClips.itemSnapshotList.items.mapTo(mutableSetOf()) { it.id }
        )
    }

    // Shake-to-clear (#4) — only listen while the app is foregrounded so the
    // accelerometer doesn't keep firing in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ShakeDetector.shakeFlow(context).collect {
                haptics.heavy()
                showClearDialog = true
            }
        }
    }

    // Autosave-on-open: Android 10+ denies clipboard reads while we're not
    // the focused window (the old background poll was rejected every cycle),
    // so capture instead at the moment focus returns. Silent = no snackbar or
    // FAB pulse; duplicates are filtered by saveIfNew.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.saveCurrentClipboardNow(silent = true)
        }
    }

    // Hoisted callbacks (stable identities)
    val onCopy: (Clip, () -> Offset?) -> Unit = remember(
        viewModel, haptics, hero, snackbarHostState, scope, context, state
    ) {
        { clip, getPosition ->
            if (!clip.isLocked) {
                haptics.light()
                ClipUtils.copyToClipboard(context, clip.content, clip.imageUri)
                viewModel.flashCopied(clip.id)
                viewModel.recordUsage(clip.id)
                val pos = getPosition()
                if (pos != null) {
                    val w = context.resources.displayMetrics.widthPixels.toFloat()
                    val h = context.resources.displayMetrics.heightPixels.toFloat()
                    hero.launch(pos, Offset(w - 160f, h - 280f))
                }
            } else {
                scope.launch { snackbarHostState.show("Locked clip — unlock it to copy") }
            }
        }
    }
    val onPin: (Clip) -> Unit = remember(viewModel, haptics) {
        { clip -> haptics.medium(); viewModel.togglePin(clip) }
    }
    val onFavorite: (Clip) -> Unit = remember(viewModel, haptics) {
        { clip -> haptics.light(); viewModel.toggleFavorite(clip) }
    }
    val onDelete: (Clip) -> Unit = remember(viewModel, haptics) {
        { clip -> haptics.heavy(); viewModel.delete(clip) }
    }
    val onLongPressEnterSelect: (Clip) -> Unit = remember(viewModel, haptics) {
        { clip -> haptics.medium(); viewModel.enterMultiSelect(clip.id) }
    }

    LaunchedEffect(Unit) {
        // collectLatest, not collect: each show() suspends for the full
        // toast duration, so a sequential collector would queue snackbars
        // behind each other — e.g. an autosave "Saved" toast would hold up
        // the delete → Undo snackbar for 3.5 s. Preemption cancels the
        // suspended show (its finally dismisses the stale item); an
        // already-tapped Undo already returned and is unaffected.
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.Copied -> Unit
                is HomeEvent.SavedNew -> {
                    if (event.success) haptics.success() else haptics.light()
                    snackbarHostState.show(
                        if (event.success) "Saved to clipboard history" else "Already in history"
                    )
                }
                is HomeEvent.EmptyClipboard -> {
                    haptics.light()
                    snackbarHostState.show("Clipboard is empty")
                }
                is HomeEvent.SensitiveSkipped -> {
                    haptics.light()
                    snackbarHostState.show("Sensitive clipboard — not saved")
                }
                is HomeEvent.Deleted -> {
                    val result = snackbarHostState.show(
                        message = "Clip deleted",
                        actionLabel = "Undo",
                        durationMs = 4_000L
                    )
                    if (result != null) {
                        haptics.success()
                        viewModel.undoDelete(event.clip)
                    }
                }
                is HomeEvent.BulkDeleted -> {
                    haptics.heavy()
                    val result = snackbarHostState.show(
                        message = "${event.clips.size} clips deleted",
                        actionLabel = "Undo",
                        durationMs = 4_500L
                    )
                    if (result != null) {
                        haptics.success()
                        viewModel.undoBulkDelete(event.clips)
                    }
                }
                is HomeEvent.ToggledPin -> {
                    snackbarHostState.show(if (event.nowPinned) "Pinned to top" else "Unpinned")
                }
                is HomeEvent.BulkPinned -> {
                    haptics.success()
                    snackbarHostState.show("${event.count} clip${if (event.count == 1) "" else "s"} updated")
                }
                HomeEvent.MonitoringPaused -> {
                    haptics.medium()
                    snackbarHostState.show("Clipboard monitoring paused — existing entries are safe")
                }
                HomeEvent.MonitoringResumed -> {
                    haptics.light()
                    snackbarHostState.show("Clipboard monitoring resumed")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = multiSelectMode,
                transitionSpec = {
                    (fadeIn() + slideInVertically { -it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "topbar"
            ) { multi ->
                if (multi) {
                    MultiSelectTopBar(
                        selectedCount = selectedIds.size,
                        totalCount = loadedCount,
                        scrollBehavior = topBarBehavior,
                        onSelectAll = {
                            haptics.light()
                            if (selectedIds.size == loadedCount) viewModel.clearSelection()
                            else viewModel.selectAll()
                        },
                        onClose = {
                            haptics.light()
                            viewModel.exitMultiSelect()
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // Slide/scale in and out instead of popping when entering or
            // leaving multi-select mode.
            AnimatedVisibility(
                visible = !multiSelectMode,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                SaveFab(
                    isPulsing = savingNow,
                    onClick = {
                        haptics.medium()
                        scope.launch { viewModel.saveCurrentClipboardNow() }
                    }
                )
            }
        },
        snackbarHost = { StackedSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = multiSelectMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                MultiSelectActionBar(
                    selectedCount = selectedIds.size,
                    totalCount = loadedCount,
                    onSelectAll = {
                        haptics.light()
                        if (selectedIds.size == loadedCount) viewModel.clearSelection()
                        else viewModel.selectAll()
                    },
                    onPin = { haptics.medium(); viewModel.bulkPin() },
                    onDelete = { haptics.heavy(); viewModel.bulkDelete() }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!multiSelectMode) {
                SearchPill(
                    queueCount = queueItems.size,
                    onOpenSearch = onOpenSearch,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenFilters = { showTypeFilterSheet = true },
                    modifier = Modifier.padding(
                        horizontal = Spacing.ScreenHorizontal,
                        vertical = Spacing.Sm
                    )
                )
                FeedFilterRow(
                    viewMode = viewMode,
                    activeFilter = activeFilter,
                    totalCount = totalCount,
                    pinnedCount = pinnedClips.size,
                    favoriteCount = favoriteCount,
                    onSelectMode = viewModel::setViewMode,
                    onSelectType = viewModel::setFilter
                )
                if (!monitoringActive) {
                    MonitoringPausedBanner(onResume = viewModel::toggleMonitoring)
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // Only trust "empty" once the refresh pass has finished.
                // During the first load — and right after an insert
                // invalidates the PagingSource (e.g. autosave-on-open) —
                // itemCount reads 0 while data is in flight; declaring empty
                // here flashed "Nothing copied yet" over a non-empty history
                // until the user toggled a filter chip.
                val refreshState = lazyClips.loadState.refresh
                val showEmpty = pinnedClips.isEmpty() &&
                    lazyClips.itemCount == 0 &&
                    refreshState is LoadState.NotLoading
              // Empty/list switch — plain `if`, no crossfade. The previous
              // AnimatedContent was firing on every filter toggle because
              // the paging refresh briefly reads `itemCount == 0`, and the
              // 200 ms fade compounded into the visible "panel shift"
              // jankiness.
              if (showEmpty) {
                val emptyTitle = when (viewMode) {
                    HomeViewMode.PINNED -> "No pinned clips"
                    HomeViewMode.FAVORITES -> "No favorites yet"
                    HomeViewMode.ALL -> "Nothing copied yet"
                }
                val emptySubtitle = when (viewMode) {
                    HomeViewMode.PINNED -> "Pin important clips to keep them here."
                    HomeViewMode.FAVORITES -> "Tap the heart on any clip to favorite it."
                    HomeViewMode.ALL -> "Copy text anywhere — it shows up here.\nShake to clear history · long-press to multi-select."
                }
                EmptyStateWithOrb(
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    modifier = Modifier
                )
            } else {
                // Per-item state has been hoisted into locals (multiSelectMode,
                    // selectedIds, justCopiedForId, viewMode, pinnedClips) so a
                    // a single field change (e.g. justCopiedForId) doesn't
                    // re-subscribe every visible clip row.
                    val contentBottom = if (multiSelectMode) 8.dp else 96.dp
                    // Modifier.animateItem() with placementSpec=null: skip the per-position
                    // spring animation entirely on scroll-driven reorders. The old
                    // animateItemPlacement() ran a spring on every scroll offset
                    // change for every visible item, which was the dominant cost
                    // in the home list (60 fps spring solve per row).
                    val itemAnimSpec = remember<androidx.compose.animation.core.FiniteAnimationSpec<Float>?> { null }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topBarBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = Spacing.Md, end = Spacing.Md, top = Spacing.Sm,
                            bottom = contentBottom
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                    ) {
                        if (pinnedClips.isNotEmpty() && viewMode != HomeViewMode.PINNED) {
                            item(key = "header_pinned", contentType = "header") {
                                FeedSectionHeader(
                                    title = "Pinned",
                                    icon = Icons.Filled.PushPin,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            SavedCountChip(pinnedClips.size)
                                            if (pinnedClips.size > 1 && !multiSelectMode) {
                                                IconButton(
                                                    onClick = {
                                                        haptics.light()
                                                        showReorderSheet = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.SwapVert,
                                                        contentDescription = "Reorder pinned",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            items(
                                items = pinnedClips.chunked(2),
                                key = { row -> "pinned_${row.first().id}" },
                                contentType = { "pinnedRow" }
                            ) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                                ) {
                                    row.forEach { clip ->
                                        PinnedCell(
                                            clip = clip,
                                            justCopied = justCopiedForId == clip.id,
                                            selected = clip.id in selectedIds,
                                            selectionMode = multiSelectMode,
                                            onCopy = { onCopy(clip) { null } },
                                            onClick = {
                                                if (multiSelectMode) viewModel.toggleSelection(clip.id)
                                                else onOpenDetail(clip.id)
                                            },
                                            onLongPress = {
                                                if (multiSelectMode) viewModel.toggleSelection(clip.id)
                                                else onLongPressEnterSelect(clip)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                    if (row.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        item(key = "header_recent", contentType = "header") {
                            FeedSectionHeader(
                                title = when (viewMode) {
                                    HomeViewMode.ALL -> "Recent Clips"
                                    HomeViewMode.FAVORITES -> "Favorites"
                                    HomeViewMode.PINNED -> "Pinned"
                                },
                                icon = when (viewMode) {
                                    HomeViewMode.ALL -> Icons.Outlined.History
                                    HomeViewMode.FAVORITES -> Icons.Outlined.FavoriteBorder
                                    HomeViewMode.PINNED -> Icons.Filled.PushPin
                                },
                                iconTint = if (viewMode == HomeViewMode.ALL) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (viewMode == HomeViewMode.PINNED &&
                                            pinnedClips.size > 1 &&
                                            !multiSelectMode
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    haptics.light()
                                                    showReorderSheet = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.SwapVert,
                                                    contentDescription = "Reorder pinned",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (!multiSelectMode) {
                                            TextButton(
                                                onClick = {
                                                    haptics.light()
                                                    viewModel.enterMultiSelect()
                                                },
                                                contentPadding = PaddingValues(horizontal = Spacing.Sm)
                                            ) {
                                                Text(
                                                    text = "Batch Select",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        items(
                            count = lazyClips.itemCount,
                            key = lazyClips.itemKey { "paged_${it.id}" },
                            contentType = lazyClips.itemContentType { "clip" }
                        ) { index ->
                            val clip = lazyClips[index] ?: return@items
                            val header = headerForClip(clip)
                            val prevClip = if (index > 0) lazyClips[index - 1] else null
                            val prevHeader = prevClip?.let { headerForClip(it) }
                            // The first "Today" bucket is implied by the section
                            // header directly above it — skip the duplicate label.
                            if (header != null && header != prevHeader && !(index == 0 && header == "Today")) {
                                DateSectionHeader(header)
                            }
                            ClipRowItem(
                                clip = clip,
                                modifier = Modifier.animateItem(itemAnimSpec),
                                multiSelectMode = multiSelectMode,
                                selectedIds = selectedIds,
                                justCopied = justCopiedForId == clip.id,
                                listState = listState,
                                onCopy = onCopy,
                                onPin = onPin,
                                onFavorite = onFavorite,
                                onDelete = onDelete,
                                onClick = { onOpenDetail(clip.id) },
                                onLongPress = { onLongPressEnterSelect(clip) },
                                onEnterMultiSelect = { viewModel.enterMultiSelect(clip.id) },
                                onToggleSelection = { viewModel.toggleSelection(clip.id) },
                                onShowCachedUrlTitle = { viewModel.showCachedUrlTitle(clip.id, clip.content) },
                                onFetchUrlTitle = { viewModel.fetchUrlTitle(clip.id, clip.content) },
                                urlTitle = urlTitles[clip.id]
                            )
                        }
                    }
            }
            // Hero copy animation overlay
            CopyHeroOverlay(
                state = hero,
                fabPosition = { null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    }

    if (showClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every saved clip (pinned items are kept).") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch { viewModel.deleteAll() }
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTypeFilterSheet) {
        TypeFilterSheet(
            activeFilter = activeFilter,
            onFilterChange = viewModel::setFilter,
            onDismiss = { showTypeFilterSheet = false }
        )
    }

    if (showQueueSheet) {
        QueueSheet(
            items = state.queueItems,
            currentIndex = state.queueIndex,
            onCopy = { item ->
                haptics.light()
                if (!item.isLocked) {
                    ClipUtils.copyToClipboard(context, item.content, item.imageUri)
                    viewModel.recordUsage(item.id)
                } else {
                    scope.launch { snackbarHostState.show("Locked clip — unlock it to copy") }
                }
            },
            onCopyNext = {
                val item = state.queueItems.getOrNull(state.queueIndex)
                if (item != null) {
                    haptics.light()
                    if (!item.isLocked) {
                        ClipUtils.copyToClipboard(context, item.content, item.imageUri)
                        viewModel.recordUsage(item.id)
                    } else {
                        scope.launch { snackbarHostState.show("Locked clip — unlock it to copy") }
                    }
                }
                scope.launch { viewModel.advanceQueue() }
            },
            onMove = { id, newIndex ->
                haptics.tick()
                scope.launch { viewModel.moveInQueue(id, newIndex) }
            },
            onRemove = { id ->
                haptics.medium()
                scope.launch { viewModel.removeFromQueue(id) }
            },
            onClear = {
                haptics.heavy()
                scope.launch { viewModel.clearQueue() }
            },
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showReorderSheet) {
        PinnedReorderSheet(
            clips = pinnedClips,
            onDismiss = { showReorderSheet = false },
            onConfirm = { orderedIds ->
                haptics.medium()
                viewModel.persistPinnedOrder(orderedIds)
                showReorderSheet = false
            }
        )
    }
}

/**
 * Non-snapshot holder for a card's last-known root position. Written from
 * onGloballyPositioned (which fires constantly while scrolling) and read
 * only when launching the copy hero animation.
 */
private class CardPositionHolder {
    var offset: Offset? = null
}

/**
 * Renders a single clip card in the list, switching between the
 * multi-select row and the normal hero row depending on [multiSelectMode].
 * Shared by the pinned section and the paged unpinned section. [modifier] is
 * supplied by the caller so it can include `LazyItemScope` extensions like
 * `animateItemPlacement`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipRowItem(
    clip: Clip,
    modifier: Modifier,
    multiSelectMode: Boolean,
    selectedIds: Set<Long>,
    justCopied: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopy: (Clip, () -> Offset?) -> Unit,
    onPin: (Clip) -> Unit,
    onFavorite: (Clip) -> Unit,
    onDelete: (Clip) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowCachedUrlTitle: () -> Unit,
    onFetchUrlTitle: () -> Unit,
    urlTitle: String? = null
) {
    if (clip.type == ClipType.URL && !clip.isLocked) {
        // Display the locally-cached title only — never a network fetch.
        // The page-title request is fired by the tap below (privacy policy:
        // "when you tap a URL entry").
        LaunchedEffect(clip.id, clip.content) { onShowCachedUrlTitle() }
    }
    val isTappableUrl = clip.type == ClipType.URL && !clip.isLocked
    val onClickWithFetch: () -> Unit = {
        if (isTappableUrl) onFetchUrlTitle()
        onClick()
    }
    val isSelected = clip.id in selectedIds
    Box(modifier) {
        if (multiSelectMode) {
            MultiSelectClipRow(
                clip = clip,
                isMultiSelect = true,
                isSelected = isSelected,
                onCopy = { onCopy(clip) { null } },
                onPin = { onPin(clip) },
                onDelete = { onDelete(clip) },
                onClick = { /* handled by onSelectionToggle */ },
                onLongPress = onEnterMultiSelect,
                onSelectionToggle = onToggleSelection
            )
        } else {
            ClipRowWithHero(
                clip = clip,
                justCopied = justCopied,
                multiSelectMode = multiSelectMode,
                listState = listState,
                onCopy = onCopy,
                onPin = onPin,
                onFavorite = onFavorite,
                onDelete = onDelete,
                onClick = onClickWithFetch,
                onLongPress = onLongPress,
                urlTitle = urlTitle
            )
        }
    }
}

/**
 * Sub-composable that bundles drag-reorder + position tracking + normal card.
 * Hoisted out of [HomeScreen] to keep the list lambda readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipRowWithHero(
    clip: Clip,
    justCopied: Boolean,
    multiSelectMode: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopy: (Clip, () -> Offset?) -> Unit,
    onPin: (Clip) -> Unit,
    onFavorite: (Clip) -> Unit,
    onDelete: (Clip) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    urlTitle: String? = null
) {
    // Plain (non-snapshot) holder for the card position: onGloballyPositioned
    // fires on every scroll frame, and a MutableState here would recompose
    // this card ~60x/s while scrolling. The value is only read at copy time
    // for the hero animation, so snapshot observation is pure overhead.
    val cardPosition = remember { CardPositionHolder() }

    SwipeableRow(
        onSwipe = { action ->
            when (action) {
                SwipeAction.Pin -> onPin(clip)
                SwipeAction.Delete -> onDelete(clip)
            }
        }
    ) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        cardPosition.offset = coords.positionInRoot()
                    }
            ) {
                NormalClipCard(
                    clip = clip,
                    justCopied = justCopied,
                    onCopy = { onCopy(clip) { cardPosition.offset } },
                onPin = { onPin(clip) },
                onFavorite = { onFavorite(clip) },
                onDelete = { onDelete(clip) },
                onClick = onClick,
                onLongPress = onLongPress,
                urlTitle = urlTitle
            )
        }
    }
}

/** Clip types that surface a compact footer action pill (mock's "Open URL" pattern). */
private val QUICK_ACTION_TYPES = setOf(ClipType.URL, ClipType.EMAIL, ClipType.PHONE)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NormalClipCard(
    clip: Clip,
    justCopied: Boolean,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    urlTitle: String? = null
) {
    val formatTime = rememberRelativeTime()
    val formatRemaining = remember { ::formatRemaining }
    val isCode = clip.type == ClipType.CODE || clip.type == ClipType.JSON
    val isUrl = clip.type == ClipType.URL && !clip.isLocked
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .semantics {
                role = Role.Button
                contentDescription = "Clip: ${clip.preview}"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            // Header — type badge · label · time | favorite · pin
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon(clip.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.Sm))
                Text(
                    text = clip.type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = Spacing.Xs)
                )
                Text(
                    text = formatTime(clip.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    AnimatedToggleIcon(
                        active = clip.isFavorite,
                        activeIcon = Icons.Filled.Favorite,
                        inactiveIcon = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (clip.isFavorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onPin,
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    AnimatedToggleIcon(
                        active = clip.isPinned,
                        activeIcon = Icons.Filled.PushPin,
                        inactiveIcon = Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (clip.isPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (clip.type == ClipType.IMAGE && clip.imageUri != null && !clip.isLocked) {
                val density = LocalDensity.current.density
                val reqWidth = (360 * density).toInt()
                val reqHeight = (112 * density).toInt()
                val bitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    clip.imageUri, reqWidth, reqHeight
                ) {
                    value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        clip.imageUri?.let { uri ->
                            runCatching {
                                ImageCopier.decodeBitmapSampled(uri, reqWidth, reqHeight)
                            }.getOrNull()
                        }
                    }
                }
                val bmp = bitmap
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Image clip, copied ${formatTime(clip.createdAt)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCode) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Xxs)
                    ) {
                        Text(
                            text = clip.preview,
                            style = if (isCode) {
                                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (isUrl) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isUrl) FontWeight.Medium else null,
                            maxLines = if (isCode) 4 else if (isUrl) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isUrl && !urlTitle.isNullOrBlank()) {
                            Text(
                                text = urlTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contextual quick action (mock footer pattern) then plain meta.
                if (!clip.isLocked && clip.type in QUICK_ACTION_TYPES) {
                    QuickActionPill(type = clip.type, content = clip.content)
                    Spacer(Modifier.width(Spacing.Sm))
                }
                Text(
                    text = clipStatsLabel(clip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (clip.hasExpiration) {
                    Text(
                        text = "Expires in ${formatRemaining(clip.expiresAt!!)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.Sm)
                    )
                }
                if (clip.hasUseLimit) {
                    Text(
                        text = "${clip.useCount}/${clip.useLimit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.Sm)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
                CopyPill(isCopied = justCopied, onClick = onCopy)
            }
            // Full-width strip only where it adds a visual the body cannot:
            // the live colour swatch. URL / EMAIL / PHONE actions live in the
            // footer pill above, so they no longer duplicate the body text.
            if (!clip.isLocked && clip.type == ClipType.COLOR_HEX) {
                InlinePreview(clip.type, clip.content)
            }
            // One-time codes get a prominent copy button
            if (clip.type == ClipType.OTP && !clip.isLocked) {
                // Regex extraction is not free on long contents — run it once
                // per clip, not on every recomposition of this row.
                val code = remember(clip.id, clip.content) {
                    ClipClassifier.extractOtp(clip.content)
                }
                if (code != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onCopy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Copy code · $code",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

/** Short human summary for the card footer pill: "74 chars" / "3 lines". */
private fun clipStatsLabel(clip: Clip): String {
    if (clip.isLocked) return "Locked"
    val content = clip.content
    val lineCount = content.count { it == '\n' } + 1
    return if (lineCount > 1) "$lineCount lines" else "${content.length} chars"
}

private fun formatRemaining(expiresAt: Long): String {
    val diff = expiresAt - System.currentTimeMillis()
    if (diff <= 0) return "now"
    val minutes = diff / 60_000
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

/**
 * Bucket a clip into Today / Yesterday / This Week / Older so the paged list
 * can show a section header at each day boundary. Buckets are computed
 * relative to `System.currentTimeMillis()` at call time, matching the previous
 * eager `groupClipsByDate` behaviour.
 */
private fun headerForClip(clip: Clip): String? {
    val cal = java.util.Calendar.getInstance()
    val startOfToday = cal.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val startOfWeek = startOfToday - (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1) * 86_400_000L
    return when {
        clip.createdAt >= startOfToday -> "Today"
        clip.createdAt >= startOfYesterday -> "Yesterday"
        clip.createdAt >= startOfWeek -> "This Week"
        else -> "Older"
    }
}

@Composable
private fun DateSectionHeader(title: String) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Spacing.Sm, horizontal = Spacing.Xs)
        )
    }
}