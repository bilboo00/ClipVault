package com.clipvault.manager.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.SaveFab
import com.clipvault.manager.ui.components.tonalSearchFieldColors
import com.clipvault.manager.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit = {},
    viewModel: TagsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    var deletingTag by remember { mutableStateOf<TagEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        floatingActionButton = {
            SaveFab(
                onClick = { showCreate = true },
                label = "New tag"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            var query by remember { mutableStateOf("") }
            if (state.tags.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                    placeholder = { Text("Search tags") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = tonalSearchFieldColors(),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
            val queryText = query
            val visibleTags = remember(state.tags, state.usageCount, queryText) {
                if (queryText.isBlank()) state.tags
                else state.tags.filter { it.name.contains(queryText, ignoreCase = true) }
            }
            if (visibleTags.isEmpty() && queryText.isNotBlank()) {
                EmptyStateWithOrb(
                    icon = Icons.Outlined.Search,
                    title = "No matching tags",
                    subtitle = "No tag matches \"$queryText\".",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (visibleTags.isEmpty()) {
                EmptyStateWithOrb(
                    icon = Icons.Outlined.Sell,
                    title = "No tags yet",
                    subtitle = "Tags let you organize clips across types and dates.",
                    actionLabel = "Create your first tag",
                    onAction = { showCreate = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.Md, vertical = Spacing.Xs
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    items(visibleTags, key = { it.id }) { tag ->
                        TagCard(
                            modifier = Modifier.animateItem(
                                placementSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                            ),
                            tag = tag,
                            usageCount = state.usageCount[tag.id] ?: 0,
                            onEdit = { editingTag = tag },
                            onDelete = { deletingTag = tag }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TagEditorDialog(
            initial = null,
            onDismiss = { showCreate = false },
            onSave = { name, color ->
                viewModel.createTag(name, color)
                showCreate = false
            }
        )
    }

    editingTag?.let { tag ->
        TagEditorDialog(
            initial = tag,
            onDismiss = { editingTag = null },
            onSave = { name, color ->
                viewModel.updateTag(tag, name, color)
                editingTag = null
            }
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("Delete tag?") },
            text = { Text("Remove the \"${tag.name}\" tag. Clips will be untagged but kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag.id)
                    deletingTag = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagCard(
    modifier: Modifier = Modifier,
    tag: TagEntity,
    usageCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = remember(tag.color) {
        runCatching { android.graphics.Color.parseColor(tag.color) }
            .getOrNull()
            ?.let { Color(it) }
            ?: Color(0xFF434574)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(Spacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$usageCount clip${if (usageCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    }
}


@Composable
private fun TagEditorDialog(
    initial: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var colorIndex by remember {
        mutableIntStateOf(
            TAG_COLORS.indexOfFirst { it == initial?.color }.takeIf { it >= 0 } ?: 0
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New tag" else "Edit tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TAG_COLORS.forEachIndexed { index, hex ->
                        val isSelected = colorIndex == index
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .clickable { colorIndex = index }
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), TAG_COLORS[colorIndex]) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF434574)
    }
}

internal val TAG_COLORS = listOf(
    "#434574", "#14B8A6", "#F59E0B", "#EF4444", "#8B5CF6",
    "#EC4899", "#10B981", "#3B82F6", "#F97316", "#6366F1"
)