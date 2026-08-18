package com.clipvault.manager.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.local.entity.TagEntity

/**
 * Bottom sheet to assign tags and collections to a single clip.
 * Both lists are toggled immediately via their DAO crossref helpers.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OrganizeSheet(
    tags: List<TagEntity>,
    collections: List<CollectionEntity>,
    selectedTagIds: Set<Long>,
    selectedCollectionIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onToggleCollection: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Organize",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            Text(
                "Assign tags and collections to this clip.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )

            SectionLabel("Tags")
            if (tags.isEmpty()) {
                Text(
                    "No tags yet. Create them from Settings → Tags.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        val primary = MaterialTheme.colorScheme.primary
                        val dotColor = remember(tag.color) {
                            runCatching { Color(android.graphics.Color.parseColor(tag.color)) }
                                .getOrDefault(primary)
                        }
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = { onToggleTag(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionLabel("Collections")
            if (collections.isEmpty()) {
                Text(
                    "No collections yet. Create them from Settings → Collections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    collections.forEach { collection ->
                        FilterChip(
                            selected = collection.id in selectedCollectionIds,
                            onClick = { onToggleCollection(collection.id) },
                            label = { Text(collection.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 24.dp)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
    )
}
