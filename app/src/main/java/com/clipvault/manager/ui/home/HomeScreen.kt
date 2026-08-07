package com.clipvault.manager.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.sensor.ShakeDetector
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.CopyHeroOverlay
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.InlinePreview
import com.clipvault.manager.ui.components.MultiSelectActionBar
import com.clipvault.manager.ui.components.MultiSelectClipRow
import com.clipvault.manager.ui.components.QueueSheet
import com.clipvault.manager.ui.components.SaveFab
import com.clipvault.manager.ui.components.StackedSnackbarHost
import com.clipvault.manager.ui.components.SwipeAction
import com.clipvault.manager.ui.components.SwipeableRow
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.components.typeIcon
import com.clipvault.manager.ui.components.draggableItem
import com.clipvault.manager.ui.components.rememberCopyHeroState
import com.clipvault.manager.ui.components.rememberStackedSnackbarHostState
import com.clipvault.manager.ui.theme.Motion
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = rememberStackedSnackbarHostState()
    val haptics = rememberHaptics()
    val hero = rememberCopyHeroState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val urlTitles by viewModel.titleMap.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Shared reorder list for pinned-clip drag reordering. Kept in sync with
    // the underlying list; mutated locally during a drag, restored on DB change.
    val reorderList = remember { mutableStateListOf<Clip>() }
    LaunchedEffect(state.clips) {
        reorderList.clear()
        reorderList.addAll(state.clips)
    }

    // Shake-to-clear (#4)
    LaunchedEffect(Unit) {
        ShakeDetector.shakeFlow(context).collect {
            haptics.heavy()
            showClearDialog = true
        }
    }

    // Hoisted callbacks (stable identities)
    val onCopy: (Clip, () -> Offset?) -> Unit = { clip, getPosition ->
        haptics.light()
        copyToClipboard(context, clip.content)
        viewModel.flashCopied(clip.id)
        viewModel.recordUsage(clip.id)
        val pos = getPosition()
        if (pos != null) {
            val w = context.resources.displayMetrics.widthPixels.toFloat()
            val h = context.resources.displayMetrics.heightPixels.toFloat()
            hero.launch(pos, Offset(w - 160f, h - 280f))
        }
    }
    val onPin: (Clip) -> Unit = { clip -> haptics.medium(); viewModel.togglePin(clip) }
    val onFavorite: (Clip) -> Unit = { clip -> haptics.light(); viewModel.toggleFavorite(clip) }
    val onDelete: (Clip) -> Unit = { clip -> haptics.heavy(); viewModel.delete(clip) }
    val onLongPressEnterSelect: (Clip) -> Unit = { clip -> haptics.medium(); viewModel.enterMultiSelect(clip.id) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Copied -> Unit
                is HomeEvent.SavedNew -> {
                    if (event.success) haptics.success() else haptics.light()
                    snackbarHostState.show(
                        if (event.success) "Saved to clipboard history" else "Already in history"
                    )
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
        topBar = {
            AnimatedContent(
                targetState = state.multiSelectMode,
                transitionSpec = {
                    (fadeIn() + slideInVertically { -it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "topbar"
            ) { multi ->
                if (multi) {
                    MultiSelectTopBar(
                        selectedCount = state.selectedIds.size,
                        totalCount = state.clips.size,
                        onSelectAll = {
                            haptics.light()
                            if (state.selectedIds.size == state.clips.size) viewModel.clearSelection()
                            else viewModel.selectAll()
                        },
                        onClose = {
                            haptics.light()
                            viewModel.exitMultiSelect()
                        }
                    )
                } else {
                    NormalTopBar(
                        count = state.clips.size,
                        monitoringActive = state.monitoringActive,
                        queueSize = state.queueItems.size,
                        onToggleMonitoring = viewModel::toggleMonitoring,
                        onOpenQueue = { showQueueSheet = true }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!state.multiSelectMode) {
                SaveFab(
                    isPulsing = state.savingNow,
                    onClick = {
                        haptics.medium()
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        try {
                            val text = cm.primaryClip?.getItemAt(0)
                                ?.coerceToText(context)?.toString().orEmpty()
                            viewModel.saveCurrentClipboard(text)
                        } catch (_: SecurityException) { }
                    }
                )
            }
        },
        snackbarHost = { StackedSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = state.multiSelectMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                MultiSelectActionBar(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.clips.size,
                    onSelectAll = {
                        haptics.light()
                        if (state.selectedIds.size == state.clips.size) viewModel.clearSelection()
                        else viewModel.selectAll()
                    },
                    onPin = { haptics.medium(); viewModel.bulkPin() },
                    onDelete = { haptics.heavy(); viewModel.bulkDelete() }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.multiSelectMode) {
                FilterChipRow(
                    activeFilter = state.activeFilter,
                    onFilterChange = viewModel::setFilter,
                    favoritesOnly = state.favoritesOnly,
                    onFavoritesChange = viewModel::setFavoritesOnly
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
             if (state.clips.isEmpty()) {
                EmptyStateWithOrb(
                    title = "Nothing copied yet",
                    subtitle = "Copy text anywhere — it shows up here.\nShake to clear history · long-press to multi-select.",
                    modifier = Modifier
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp,
                        bottom = if (state.multiSelectMode) 8.dp else 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = groupClipsByDate(state.clips)
                    grouped.forEach { (header, clips) ->
                        stickyHeader(key = "header_$header", contentType = "header") {
                            DateSectionHeader(header)
                        }
                       items(clips, key = { it.id }, contentType = { "clip" }) { clip ->
                          // Trigger background fetch for URL clips so the preview
                          // title shows up once the network request completes.
                          if (clip.type == ClipType.URL) {
                              LaunchedEffect(clip.id) { viewModel.fetchUrlTitle(clip.content) }
                          }
                          val isSelected = clip.id in state.selectedIds
                         when {
                             state.multiSelectMode -> {
                                 MultiSelectClipRow(
                                     clip = clip,
                                     isMultiSelect = true,
                                     isSelected = isSelected,
                                     onCopy = { onCopy(clip) { null } },
                                     onPin = { onPin(clip) },
                                     onDelete = { onDelete(clip) },
                                     onClick = { /* handled by onSelectionToggle */ },
                                     onLongPress = { viewModel.enterMultiSelect(clip.id) },
                                     onSelectionToggle = { viewModel.toggleSelection(clip.id) }
                                 )
                             }
                             else -> {
                           ClipRowWithHero(
                               clip = clip,
                               justCopied = state.justCopiedForId == clip.id,
                               multiSelectMode = state.multiSelectMode,
                               listState = listState,
                               reorderList = reorderList,
                               onCopy = onCopy,
                               onPin = onPin,
                               onFavorite = onFavorite,
                               onDelete = onDelete,
                               onClick = { onOpenDetail(clip.id) },
                               onLongPress = { onLongPressEnterSelect(clip) },
                               onReorderPinned = { newOrder ->
                                   scope.launch { viewModel.persistPinnedOrder(newOrder) }
                               },
                               urlTitle = urlTitles[clip.content]
                           )
                             }
                          }
                      }
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

    if (showQueueSheet) {
        QueueSheet(
            items = state.queueItems,
            currentIndex = state.queueIndex,
            onCopy = { item ->
                haptics.light()
                copyToClipboard(context, item.content)
                viewModel.recordUsage(item.id)
            },
            onCopyNext = {
                val item = state.queueItems.getOrNull(state.queueIndex)
                if (item != null) {
                    haptics.light()
                    copyToClipboard(context, item.content)
                    viewModel.recordUsage(item.id)
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
    reorderList: androidx.compose.runtime.snapshots.SnapshotStateList<Clip>,
    onCopy: (Clip, () -> Offset?) -> Unit,
    onPin: (Clip) -> Unit,
    onFavorite: (Clip) -> Unit,
    onDelete: (Clip) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReorderPinned: (List<Long>) -> Unit,
    urlTitle: String? = null
) {
    val cardPosition = remember { mutableStateOf<Offset?>(null) }

    // Only pinned clips can be drag-reordered, and only outside selection mode.
    val dragModifier = if (clip.isPinned && !multiSelectMode) {
        Modifier.draggableItem(
            listState = listState,
            itemId = clip.id,
            items = reorderList,
            equalityOf = { it.id },
            onDragEnd = {
                // Persist the complete pinned order once per drag.
                onReorderPinned(reorderList.filter { it.isPinned }.map { it.id })
            }
        )
    } else {
        Modifier
    }

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
                    cardPosition.value = coords.positionInRoot()
                }
                .then(dragModifier)
        ) {
            NormalClipCard(
                clip = clip,
                justCopied = justCopied,
                onCopy = { onCopy(clip) { cardPosition.value } },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(
    count: Int,
    monitoringActive: Boolean,
    queueSize: Int,
    onToggleMonitoring: () -> Unit,
    onOpenQueue: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text("Clipboard", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "$count saved · ${if (monitoringActive) "live" else "paused"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Icons.Outlined.Queue,
                    contentDescription = "Paste queue",
                    tint = if (queueSize > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleMonitoring) {
                Icon(
                    imageVector = if (monitoringActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (monitoringActive) "Pause monitoring" else "Resume monitoring",
                    tint = if (monitoringActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Exit selection")
            }
        },
        title = { Text("$selectedCount selected", style = MaterialTheme.typography.titleLarge) },
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(if (selectedCount == totalCount) "Clear" else "All", style = MaterialTheme.typography.titleMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (clip.isPinned) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (clip.isPinned) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (clip.type == ClipType.IMAGE && clip.imageUri != null) {
                val bitmap = remember(clip.imageUri) {
                    try {
                        android.graphics.BitmapFactory.decodeFile(clip.imageUri)
                            ?.let { android.graphics.Bitmap.createScaledBitmap(it, 400, 300, true) }
                    } catch (_: Exception) { null }
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image clip",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = clip.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (clip.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (clip.isFavorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPin) {
                    Icon(
                        imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (clip.isPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeBadge(clip.type)
                    Text(
                        text = formatTime(clip.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (clip.hasExpiration) {
                        Text(
                            text = "⏳ ${formatRemaining(clip.expiresAt!!)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (clip.hasUseLimit) {
                        Text(
                            text = "${clip.useCount}/${clip.useLimit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedCopyButton(isCopied = justCopied, onClick = onCopy)
                }
            }
            // Inline preview (#6)
            val showPreview = clip.type in setOf(
                ClipType.COLOR_HEX, ClipType.PHONE, ClipType.EMAIL, ClipType.URL
            )
            if (showPreview) {
                InlinePreview(clip.type, clip.content)
            }
            // URL preview title (fetched in background)
            if (clip.type == ClipType.URL && !urlTitle.isNullOrBlank()) {
                Text(
                    text = urlTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // One-time codes get a prominent copy button
            if (clip.type == ClipType.OTP) {
                val code = ClipClassifier.extractOtp(clip.content)
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

private fun copyToClipboard(context: Context, content: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("clip", content))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    activeFilter: ClipType?,
    onFilterChange: (ClipType?) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = favoritesOnly,
            onClick = { onFavoritesChange(!favoritesOnly) },
            label = { Text("Favorites") },
            leadingIcon = {
                Icon(
                    imageVector = if (favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = activeFilter == null,
            onClick = { onFilterChange(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        ClipType.entries.forEach { type ->
            FilterChip(
                selected = activeFilter == type,
                onClick = {
                    onFilterChange(if (activeFilter == type) null else type)
                },
                label = { Text(type.label()) },
                leadingIcon = {
                    Icon(
                        imageVector = typeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

private fun formatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000}d ago"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
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

private fun groupClipsByDate(clips: List<Clip>): List<Pair<String, List<Clip>>> {
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance()
    val startOfToday = cal.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val startOfWeek = startOfToday - (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1) * 86_400_000L

    val pinned = clips.filter { it.isPinned }
    val unpinned = clips.filter { !it.isPinned }

    val today = unpinned.filter { it.createdAt >= startOfToday }
    val yesterday = unpinned.filter { it.createdAt in startOfYesterday until startOfToday }
    val thisWeek = unpinned.filter { it.createdAt in startOfWeek until startOfYesterday }
    val older = unpinned.filter { it.createdAt < startOfWeek }

    return buildList {
        if (pinned.isNotEmpty()) add("Pinned" to pinned)
        if (today.isNotEmpty()) add("Today" to today)
        if (yesterday.isNotEmpty()) add("Yesterday" to yesterday)
        if (thisWeek.isNotEmpty()) add("This Week" to thisWeek)
        if (older.isNotEmpty()) add("Older" to older)
    }
}

@Composable
private fun DateSectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
    }
}