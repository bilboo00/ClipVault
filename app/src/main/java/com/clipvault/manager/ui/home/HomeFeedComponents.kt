package com.clipvault.manager.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import com.clipvault.manager.ui.components.CopyPill
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.components.typeIcon
import com.clipvault.manager.ui.theme.ClayFixed
import com.clipvault.manager.ui.theme.LavenderSecondaryFixed
import com.clipvault.manager.ui.theme.Spacing
import com.clipvault.manager.util.rememberRelativeTime

/** 56dp search pill — opens the search screen; trailing queue + filter actions. */
@Composable
fun SearchPill(
    queueCount: Int,
    onOpenSearch: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenSearch,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = Spacing.Lg, end = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Md))
            Text(
                text = "Search copied items…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Queue,
                    contentDescription = "Paste queue",
                    tint = if (queueCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenFilters, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Filter by type",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Single scrollable chip row: view modes with counts + quick type filters. */
@Composable
fun FeedFilterRow(
    viewMode: HomeViewMode,
    activeFilter: ClipType?,
    totalCount: Int,
    pinnedCount: Int,
    favoriteCount: Int,
    onSelectMode: (HomeViewMode) -> Unit,
    onSelectType: (ClipType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        FilterPill(
            label = "All ($totalCount)",
            icon = Icons.Outlined.ContentPaste,
            selected = viewMode == HomeViewMode.ALL,
            onClick = { onSelectMode(HomeViewMode.ALL) }
        )
        FilterPill(
            label = "Pinned ($pinnedCount)",
            icon = Icons.Outlined.PushPin,
            selected = viewMode == HomeViewMode.PINNED,
            onClick = { onSelectMode(HomeViewMode.PINNED) }
        )
        FilterPill(
            label = "Favorites ($favoriteCount)",
            icon = Icons.Outlined.FavoriteBorder,
            selected = viewMode == HomeViewMode.FAVORITES,
            onClick = { onSelectMode(HomeViewMode.FAVORITES) }
        )
        FilterPill(
            label = "Links",
            icon = Icons.Outlined.Link,
            selected = activeFilter == ClipType.URL,
            onClick = { onSelectType(if (activeFilter == ClipType.URL) null else ClipType.URL) }
        )
        FilterPill(
            label = "Code",
            icon = Icons.Outlined.Code,
            selected = activeFilter == ClipType.CODE,
            onClick = { onSelectType(if (activeFilter == ClipType.CODE) null else ClipType.CODE) }
        )
        FilterPill(
            label = "Colors & Text",
            icon = Icons.Outlined.Palette,
            selected = activeFilter == ClipType.COLOR_HEX,
            onClick = { onSelectType(if (activeFilter == ClipType.COLOR_HEX) null else ClipType.COLOR_HEX) }
        )
    }
}

@Composable
fun FilterPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            // DESIGN.md: unselected chips carry a subtle outline-variant border
            // and no fill; selected chips drop the outline for the tonal fill.
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .height(32.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (selected) Icons.Outlined.Check else icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Section header row: leading icon + title + optional trailing affordance. */
@Composable
fun FeedSectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Small "N Saved" counter chip used next to the Pinned header. */
@Composable
fun SavedCountChip(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    ) {
        Text(
            text = "$count Saved",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 3.dp)
        )
    }
}

/** Shown under the header only while clipboard monitoring is paused. */
@Composable
fun MonitoringPausedBanner(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onResume,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Xs),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Monitoring paused — tap to resume",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Peach = secret-ish types, lavender = everything else (mock's two tints). */
private val PinnedSecretTypes = setOf(ClipType.OTP, ClipType.IBAN, ClipType.WALLET_ADDRESS)

/** Compact tinted card for the 2-up Pinned grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedCell(
    clip: Clip,
    justCopied: Boolean,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onCopy: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatTime = rememberRelativeTime()
    val isSecret = clip.type in PinnedSecretTypes
    val container = if (isSecret) ClayFixed.copy(alpha = 0.30f)
    else LavenderSecondaryFixed.copy(alpha = 0.40f)
    val accent = if (isSecret) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .semantics {
                role = Role.Button
                contentDescription = "Pinned clip: ${clip.preview}"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = typeIcon(clip.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacing.Xs))
                Text(
                    text = clip.type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Sm))
            if (clip.type == ClipType.COLOR_HEX && !clip.isLocked) {
                MiniColorRow(hex = clip.content)
            } else {
                Text(
                    text = clip.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Spacing.Md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(clip.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                CopyPill(isCopied = justCopied, onClick = onCopy)
            }
        }
    }
}

@Composable
private fun MiniColorRow(hex: String) {
    var color by remember(hex) { mutableStateOf<Color?>(null) }
    LaunchedEffect(hex) {
        color = ClipClassifier.parseHexColor(hex)?.let { Color(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color ?: Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(Spacing.Sm))
        Text(
            text = hex.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
