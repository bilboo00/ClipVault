package com.clipvault.manager.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-focus the search field + raise the keyboard on entry
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Find in history") },
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
                singleLine = true
            )

            AnimatedVisibility(
                visible = state.query.isBlank(),
                enter = fadeIn(animationSpec = tween(Motion.Medium)),
                exit = fadeOut(animationSpec = tween(Motion.Short))
            ) {
                Hint(
                    icon = Icons.Outlined.History,
                    title = "Search your clipboard history",
                    body = "Type to find anything you've copied. Matches are highlighted."
                )
            }

            AnimatedVisibility(
                visible = state.query.isNotBlank() && state.results.isEmpty(),
                enter = fadeIn(animationSpec = tween(Motion.Medium)),
                exit = fadeOut(animationSpec = tween(Motion.Short))
            ) {
                Hint(
                    icon = Icons.Outlined.Search,
                    title = "No matches",
                    body = "Nothing in your history matches \"${state.query}\"."
                )
            }

            if (state.query.isNotBlank() && state.results.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results, key = { it.id }) { clip ->
                        ResultRow(
                            clip = clip,
                            highlight = state.query,
                            onCopy = {
                                copy(context, clip.content)
                                viewModel.recordUsage(clip.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(clip: Clip, highlight: String, onCopy: () -> Unit) {
    val haptics = rememberHaptics()
    val primaryColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.light()
                onCopy()
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = highlightText(clip.content, highlight, primaryColor),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TypeBadge(clip.type)
                AnimatedCopyButton(
                    isCopied = false,
                    onClick = {
                        haptics.light()
                        onCopy()
                    }
                )
            }
        }
    }
}

@Composable
private fun Hint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
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

private fun copy(context: Context, content: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("clip", content))
}