@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.clipvault.manager.ui.favorites

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.AnimatedToggleIcon
import com.clipvault.manager.ui.components.CopyPill
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.MultiSelectActionBar
import com.clipvault.manager.ui.components.MultiSelectTopBar
import com.clipvault.manager.ui.components.SelectionIndicator
import com.clipvault.manager.ui.components.StackedSnackbarHost
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.components.rememberStackedSnackbarHostState
import com.clipvault.manager.ui.components.typeIcon
import com.clipvault.manager.ui.home.FilterPill
import com.clipvault.manager.ui.theme.LavenderFixed
import com.clipvault.manager.ui.theme.Spacing
import com.clipvault.manager.util.ClipUtils
import com.clipvault.manager.util.ImageCopier
import com.clipvault.manager.util.rememberRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Secret-ish types get the clay-tinted, masked treatment. */
private val SecretTypes = setOf(ClipType.OTP, ClipType.IBAN, ClipType.WALLET_ADDRESS)

private val HexRegex = Regex("#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b")

/**
 * "Favorites & Pinned" — everything the user deliberately saved (pinned or
 * favorited), with per-type card variants: masked secrets, hex swatch grids,
 * link cards and text blurbs.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenDetail: (Long) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val snackbarHostState = rememberStackedSnackbarHostState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel, haptics, snackbarHostState) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FavoritesEvent.Deleted -> {
                    val result = snackbarHostState.show("Clip deleted", actionLabel = "Undo")
                    if (result != null) {
                        haptics.success()
                        viewModel.undoDelete(event.clip)
                    }
                }
                is FavoritesEvent.BulkDeleted -> {
                    haptics.success()
                    val result = snackbarHostState.show(
                        "${event.clips.size} clip${if (event.clips.size == 1) "" else "s"} deleted",
                        actionLabel = "Undo"
                    )
                    if (result != null) {
                        haptics.success()
                        viewModel.undoBulkDelete(event.clips)
                    }
                }
                is FavoritesEvent.BulkPinned -> {
                    haptics.success()
                    snackbarHostState.show(
                        "${event.count} clip${if (event.count == 1) "" else "s"} " +
                            if (event.nowPinned) "pinned" else "unpinned"
                    )
                }
                is FavoritesEvent.ToggledPin -> {
                    snackbarHostState.show(if (event.nowPinned) "Pinned" else "Unpinned")
                }
            }
        }
    }

    // Copies the clip (or a single swatch when [override] is set) through the
    // same util the Home feed uses, then flashes the card's copy affordance.
    val onCopy: (Clip, String?) -> Unit = remember(viewModel, haptics, context, snackbarHostState, scope) {
        { clip, override ->
            if (clip.isLocked) {
                scope.launch { snackbarHostState.show("Locked clip — unlock it to copy") }
            } else {
                haptics.light()
                ClipUtils.copyToClipboard(context, override ?: clip.content, clip.imageUri)
                viewModel.flashCopied(clip.id)
                viewModel.recordUsage(clip.id)
            }
        }
    }

    val emptyTitle: String
    val emptySubtitle: String
    when (state.segment) {
        SavedSegment.ALL -> {
            emptyTitle = "No saved items yet"
            emptySubtitle = "Pin or favorite clips from the Clipboard feed to keep them here."
        }
        SavedSegment.PINNED -> {
            emptyTitle = "No pinned clips"
            emptySubtitle = "Pin important clips to keep them here."
        }
        SavedSegment.LIKED -> {
            emptyTitle = "No favorites yet"
            emptySubtitle = "Tap the heart on any clip to save it here."
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = state.selectionMode,
                transitionSpec = {
                    (fadeIn() + slideInVertically { -it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "fav-topbar"
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
                            viewModel.exitSelectionMode()
                        }
                    )
                }
            }
        },
        snackbarHost = { StackedSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = state.selectionMode,
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
                    onPin = { haptics.medium(); viewModel.bulkTogglePin() },
                    onDelete = { haptics.heavy(); viewModel.bulkDelete() }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.Md,
                end = Spacing.Md,
                top = Spacing.Lg,
                bottom = if (state.selectionMode) Spacing.Sm else 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            item(key = "header", contentType = "header") {
                FavoritesHeader(
                    totalSaved = state.totalSaved,
                    selectionMode = state.selectionMode,
                    onBatchSelect = {
                        haptics.medium()
                        viewModel.enterSelectionMode()
                    }
                )
            }
            item(key = "segments", contentType = "segments") {
                SegmentRow(
                    segment = state.segment,
                    onSelect = { haptics.light(); viewModel.setSegment(it) }
                )
            }
            if (state.clips.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyStateWithOrb(title = emptyTitle, subtitle = emptySubtitle)
                }
            } else {
                items(items = state.clips, key = { it.id }, contentType = { "saved" }) { clip ->
                    SavedClipCard(
                        clip = clip,
                        justCopied = state.justCopiedId == clip.id,
                        selectionMode = state.selectionMode,
                        selected = clip.id in state.selectedIds,
                        onCopy = { onCopy(clip, null) },
                        onCopyHex = { hex -> onCopy(clip, hex) },
                        onOpenUrl = {
                            if (!clip.isLocked) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(clip.content))
                                    )
                                }
                            }
                        },
                        onPin = { haptics.medium(); viewModel.togglePin(clip) },
                        onFavorite = { haptics.light(); viewModel.toggleFavorite(clip) },
                        onDelete = { haptics.heavy(); viewModel.delete(clip) },
                        onClick = {
                            if (state.selectionMode) viewModel.toggleSelection(clip.id)
                            else onOpenDetail(clip.id)
                        },
                        onLongPress = {
                            haptics.medium()
                            viewModel.enterSelectionMode(clip.id)
                        }
                    )
                }
            }
        }
    }
}

/** Title + "N saved items available offline" + batch-select toggle. */
@Composable
private fun FavoritesHeader(
    totalSaved: Int,
    selectionMode: Boolean,
    onBatchSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Favorites & Pinned",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.Xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(Spacing.Xs))
                Text(
                    text = if (totalSaved == 0) "Nothing saved yet"
                    else "$totalSaved saved item${if (totalSaved == 1) "" else "s"} available offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    enabled = !selectionMode,
                    role = Role.Button,
                    onClick = onBatchSelect
                )
                .semantics { contentDescription = "Select multiple" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Checklist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** All Saved / Pinned Only / Liked Only segments. */
@Composable
private fun SegmentRow(
    segment: SavedSegment,
    onSelect: (SavedSegment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        FilterPill(
            label = "All Saved",
            icon = Icons.Outlined.Check,
            selected = segment == SavedSegment.ALL,
            onClick = { onSelect(SavedSegment.ALL) }
        )
        FilterPill(
            label = "Pinned Only",
            icon = Icons.Outlined.PushPin,
            selected = segment == SavedSegment.PINNED,
            onClick = { onSelect(SavedSegment.PINNED) }
        )
        FilterPill(
            label = "Liked Only",
            icon = Icons.Outlined.FavoriteBorder,
            selected = segment == SavedSegment.LIKED,
            onClick = { onSelect(SavedSegment.LIKED) }
        )
    }
}

@Composable
private fun SavedClipCard(
    clip: Clip,
    justCopied: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onCopy: () -> Unit,
    onCopyHex: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val formatTime = rememberRelativeTime()
    var menuOpen by remember { mutableStateOf(false) }
    val isSecret = clip.type in SecretTypes
    val isColor = clip.type == ClipType.COLOR_HEX && !clip.isLocked
    val isUrl = clip.type == ClipType.URL && !clip.isLocked
    val isImage = clip.type == ClipType.IMAGE && clip.imageUri != null && !clip.isLocked
    val clayTint = isSecret || clip.isLocked

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .semantics {
                role = Role.Button
                contentDescription = "Saved clip: ${clip.preview}"
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (clayTint) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            // Header — tinted type circle + label | pinned pill + toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeCircle(clip)
                Spacer(Modifier.width(Spacing.Sm))
                Text(
                    text = if (clip.isLocked) "Locked" else clip.type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selectionMode) {
                    SelectionIndicator(selected = selected)
                } else {
                    if (clip.isPinned) {
                        PinnedPill()
                        Spacer(Modifier.width(Spacing.Xs))
                    }
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
                    // Kebab menu — the mock surfaces the secondary actions
                    // (copy, pin, favorite, delete) behind ⋮ on every card.
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .size(32.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onCopy()
                                }
                            )
                            if (isUrl) {
                                DropdownMenuItem(
                                    text = { Text("Open link") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.OpenInNew,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onOpenUrl()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (clip.isPinned) "Unpin" else "Pin to top") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (clip.isPinned) Icons.Filled.PushPin
                                        else Icons.Outlined.PushPin,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onPin()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (clip.isFavorite) "Remove from favorites"
                                        else "Add to favorites"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (clip.isFavorite) Icons.Filled.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onFavorite()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Body variant
            when {
                isColor -> ColorSwatches(clip = clip, onCopyHex = onCopyHex)
                isSecret || clip.isLocked -> MaskedSecretBody(clip = clip)
                isUrl -> UrlBody(clip = clip)
                isImage -> ImageBody(clip = clip)
                else -> QuoteBody(clip = clip)
            }

            // Footer — context on the left, copy affordance on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    if (!isColor && !isUrl && !isImage && !isSecret && !clip.isLocked) {
                        StatsPill(clipStatsLabel(clip))
                        if (clip.useCount > 0) {
                            Text(
                                text = "Used ${clip.useCount} times",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = if (clip.isLocked) "Locked for privacy"
                            else formatTime(clip.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    if (isUrl) {
                        AnimatedCopyButton(isCopied = justCopied, onClick = onCopy)
                        OpenPill(onClick = onOpenUrl)
                    } else {
                        CopyPill(isCopied = justCopied, onClick = onCopy)
                    }
                }
            }
        }
    }
}

/** 32dp tinted circle holding the clip-type glyph. */
@Composable
private fun TypeCircle(clip: Clip) {
    val container: Color
    val content: Color
    when {
        clip.isLocked || clip.type in SecretTypes -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }
        clip.type == ClipType.COLOR_HEX -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }
        clip.type == ClipType.URL -> {
            container = LavenderFixed
            content = MaterialTheme.colorScheme.primary
        }
        else -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (clip.isLocked) Icons.Outlined.Lock else typeIcon(clip.type),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Masked content pod with a reveal (eye) toggle — secrets and locked clips. */
@Composable
private fun MaskedSecretBody(clip: Clip) {
    var revealed by rememberSaveable(clip.id) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.Sm))
            Text(
                text = when {
                    clip.isLocked -> Clip.LOCKED_PLACEHOLDER
                    revealed -> clip.content
                    else -> maskSecret(clip.content)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (revealed) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!clip.isLocked) {
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** White pod with a tappable hex swatch grid (up to 4 colours). */
@Composable
private fun ColorSwatches(clip: Clip, onCopyHex: (String) -> Unit) {
    val swatches = remember(clip.id, clip.content) { extractHexCodes(clip.content) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        Text(
            text = "Tap a swatch to copy its hex value.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        swatches.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
            ) {
                row.forEach { hex ->
                    SwatchPod(
                        hex = hex,
                        onClick = { onCopyHex(hex) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SwatchPod(hex: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var swatchColor by remember(hex) { mutableStateOf<Color?>(null) }
    LaunchedEffect(hex) {
        swatchColor = ClipClassifier.parseHexColor(hex)?.let { Color(it) }
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(swatchColor ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            )
            Text(
                text = hex,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $hex",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** White pod with the underlined link text. */
@Composable
private fun UrlBody(clip: Clip) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = clip.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Thumbnail pod for saved image clips; falls back to the text body. */
@Composable
private fun ImageBody(clip: Clip) {
    val uri = clip.imageUri ?: return
    val density = LocalDensity.current.density
    val reqWidth = (360 * density).toInt()
    val reqHeight = (150 * density).toInt()
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        uri, reqWidth, reqHeight
    ) {
        value = withContext(Dispatchers.Default) {
            runCatching { ImageCopier.decodeBitmapSampled(uri, reqWidth, reqHeight) }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Saved image clip",
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        QuoteBody(clip = clip)
    }
}

/** White "quote" pod with the clip text, max 3 lines. */
@Composable
private fun QuoteBody(clip: Clip) {    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Text(
            text = "“${clip.preview}”",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Md)
        )
    }
}

/** Filled primary "Open" pill for URL cards. */
@Composable
private fun OpenPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** Clay "Pinned" pill shown on pinned cards. */
@Composable
private fun PinnedPill() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Pinned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}

/** Small "74 chars" / "3 lines" pill used in text-card footers. */
@Composable
private fun StatsPill(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 3.dp)
        )
    }
}

/** "sk_test_51••••••••Wq9L" — keep a recognisable head and tail only. */
private fun maskSecret(content: String): String = when {
    content.length > 20 -> content.take(6) + "••••••••••••" + content.takeLast(4)
    content.length > 8 -> content.take(2) + "••••••••" + content.takeLast(2)
    else -> "•".repeat(content.length.coerceAtLeast(4))
}

/** Up to 4 unique hex colours in [content], in order of appearance. */
private fun extractHexCodes(content: String): List<String> {
    val found = HexRegex.findAll(content)
        .map { it.value.uppercase() }
        .distinct()
        .take(4)
        .toList()
    return found.ifEmpty { listOf(content.trim().uppercase()) }
}

/** Short human summary for the footer pill: "74 chars" / "3 lines". */
private fun clipStatsLabel(clip: Clip): String {
    if (clip.isLocked) return "Locked"
    val content = clip.content
    val lineCount = content.count { it == '\n' } + 1
    return if (lineCount > 1) "$lineCount lines" else "${content.length} chars"
}
