package com.clipvault.manager.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.components.tonalSearchFieldColors
import com.clipvault.manager.ui.components.typeIcon
import com.clipvault.manager.ui.theme.Motion
import com.clipvault.manager.ui.theme.Spacing
import com.clipvault.manager.util.ClipUtils
import com.clipvault.manager.util.ImageCopier
import com.clipvault.manager.util.rememberRelativeTime
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenDetail: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Note: we deliberately do NOT auto-focus the search field on screen
    // entry. Auto-focus + auto-keyboard popped the IME in front of every
    // navigation into Search, forcing the user to dismiss it before they
    // could browse or jump to a specific tab. The search field only
    // receives focus when the user explicitly taps it.

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
                    .focusRequester(focusRequester),
                placeholder = { Text("Find in history") },
                shape = RoundedCornerShape(28.dp),
                colors = tonalSearchFieldColors(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = state.query.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
                    ) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submitSearch()
                        keyboard?.hide()
                    }
                )
            )

            SearchScopeSegmentedRow(
                scope = state.scope,
                onScopeChange = viewModel::setScope
            )
            SearchTypeFilterRow(
                activeFilter = state.typeFilter,
                onFilterChange = viewModel::setTypeFilter
            )

            AnimatedVisibility(
                visible = state.query.isBlank() && state.recentSearches.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(Motion.Medium)),
                exit = fadeOut(animationSpec = tween(Motion.Short))
            ) {
                RecentSearchesRow(
                    recents = state.recentSearches,
                    onSelect = viewModel::setQuery,
                    onClear = viewModel::clearRecentSearches
                )
            }

            AnimatedVisibility(
                visible = state.query.isBlank() && state.recentSearches.isEmpty(),
                enter = fadeIn(animationSpec = tween(Motion.Medium)),
                exit = fadeOut(animationSpec = tween(Motion.Short))
            ) {
                EmptyStateWithOrb(
                    icon = Icons.Outlined.History,
                    title = "Search your clipboard history",
                    subtitle = "Type to find anything you've copied. Matches are highlighted.",
                    modifier = Modifier.fillMaxSize()
                )
            }

            AnimatedVisibility(
                visible = state.query.isNotBlank() && state.results.isEmpty(),
                enter = fadeIn(animationSpec = tween(Motion.Medium)),
                exit = fadeOut(animationSpec = tween(Motion.Short))
            ) {
                EmptyStateWithOrb(
                    icon = Icons.Outlined.Search,
                    title = "No matches",
                    subtitle = "Nothing in your history matches \"${state.query}\".",
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (state.query.isNotBlank() && state.results.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.Md, vertical = Spacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    items(state.results, key = { it.id }) { clip ->
                        ResultRow(
                            clip = clip,
                            highlight = state.query,
                            justCopied = state.justCopiedId == clip.id,
                            onOpen = { onOpenDetail(clip.id) },
                            onCopy = {
                                if (!clip.isLocked) {
                                    haptics.light()
                                    ClipUtils.copyToClipboard(context, clip.content, clip.imageUri)
                                    viewModel.recordUsage(clip.id)
                                    viewModel.flashCopied(clip.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    clip: Clip,
    highlight: String,
    justCopied: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val formatTime = rememberRelativeTime()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics {
                role = Role.Button
                contentDescription = "Clip: ${clip.preview}"
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.CardPadding)) {
            if (clip.isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = Clip.LOCKED_PLACEHOLDER,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (clip.type == com.clipvault.manager.data.local.entity.ClipType.IMAGE && clip.imageUri != null) {
                val density = LocalDensity.current.density
                val reqWidth = (360 * density).toInt()
                val reqHeight = (96 * density).toInt()
                val bmp by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    clip.imageUri, reqWidth, reqHeight
                ) {
                    value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        clip.imageUri?.let { uri ->
                            runCatching { ImageCopier.decodeBitmapSampled(uri, reqWidth, reqHeight) }.getOrNull()
                        }
                    }
                }
                val bitmap = bmp
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image clip, copied ${formatTime(clip.createdAt)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            } else {
                Text(
                    text = remember(clip.content, highlight, primaryColor) {
                        highlightText(clip.content, highlight, primaryColor)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(clip.type)
                    Text(
                        text = formatTime(clip.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.Sm)
                    )
                }
                AnimatedCopyButton(
                    isCopied = justCopied,
                    onClick = onCopy
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScopeSegmentedRow(
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit
) {
    val modes = listOf(
        Triple(SearchScope.ALL, "All", Icons.Outlined.Search),
        Triple(SearchScope.PINNED, "Pinned", Icons.Outlined.PushPin),
        Triple(SearchScope.FAVORITES, "Favorites", Icons.Outlined.FavoriteBorder)
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Md, vertical = Spacing.Xs)
    ) {
        modes.forEachIndexed { index, (mode, label, icon) ->
            val selected = scope == mode
            SegmentedButton(
                selected = selected,
                onClick = { onScopeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = selected) {
                        Icon(icon, contentDescription = null)
                    }
                },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTypeFilterRow(
    activeFilter: ClipType?,
    onFilterChange: (ClipType?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
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

@Composable
private fun RecentSearchesRow(
    recents: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            recents.forEach { recent ->
                SuggestionChip(
                    onClick = { onSelect(recent) },
                    label = { Text(recent) },
                    icon = {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}



/**
 * Highlights every occurrence of [query] inside [text] with the primary color.
 */
private fun highlightText(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var index = 0
        val lowered = text.lowercase()
        val q = query.lowercase()
        while (index < text.length) {
            val found = lowered.indexOf(q, index)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                append(text.substring(found, found + q.length))
            }
            index = found + q.length
        }
    }
}