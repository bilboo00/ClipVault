package com.clipvault.manager.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.ui.components.EmptyStateWithOrb
import com.clipvault.manager.ui.components.IconBadge
import com.clipvault.manager.ui.components.SaveFab
import com.clipvault.manager.ui.components.tonalSearchFieldColors
import com.clipvault.manager.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var editingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var deletingCollection by remember { mutableStateOf<CollectionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collections") },
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
                label = "New collection"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            var query by remember { mutableStateOf("") }
            if (state.collections.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                    placeholder = { Text("Search collections") },
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
            val q = query
            val visible = remember(state.collections, q) {
                if (q.isBlank()) state.collections
                else state.collections.filter { it.name.contains(q, ignoreCase = true) }
            }
            when {
                visible.isEmpty() && q.isNotBlank() -> EmptyStateWithOrb(
                    icon = Icons.Outlined.Search,
                    title = "No matching collections",
                    subtitle = "No collection matches \"$q\".",
                    modifier = Modifier.fillMaxSize()
                )
                visible.isEmpty() -> EmptyStateWithOrb(
                    icon = Icons.Outlined.Folder,
                    title = "No collections yet",
                    subtitle = "Group related clips — work, school, recipes, projects.",
                    actionLabel = "Create your first collection",
                    onAction = { showCreate = true },
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.Md, vertical = Spacing.Xs
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    items(visible, key = { it.id }) { collection ->
                        CollectionCard(
                            modifier = Modifier.animateItem(
                                placementSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                            ),
                            collection = collection,
                            usageCount = state.usageCount[collection.id] ?: 0,
                            onEdit = { editingCollection = collection },
                            onDelete = { deletingCollection = collection }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CollectionEditorDialog(
            initial = null,
            onDismiss = { showCreate = false },
            onSave = { name ->
                viewModel.createCollection(name)
                showCreate = false
            }
        )
    }

    editingCollection?.let { collection ->
        CollectionEditorDialog(
            initial = collection,
            onDismiss = { editingCollection = null },
            onSave = { name ->
                viewModel.updateCollection(collection, name)
                editingCollection = null
            }
        )
    }

    deletingCollection?.let { collection ->
        AlertDialog(
            onDismissRequest = { deletingCollection = null },
            title = { Text("Delete collection?") },
            text = { Text("Remove \"${collection.name}\". Clips will stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(collection.id)
                    deletingCollection = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCollection = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CollectionCard(
    modifier: Modifier = Modifier,
    collection: CollectionEntity,
    usageCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
            IconBadge(Icons.Outlined.Folder)
            Spacer(Modifier.width(Spacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
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
private fun CollectionEditorDialog(
    initial: CollectionEntity?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New collection" else "Edit collection") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim()) }
            ) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}