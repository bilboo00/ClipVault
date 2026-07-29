package com.clipvault.manager.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.domain.TextTransformer
import com.clipvault.manager.domain.TransformationResult
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.theme.Motion
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipDetailScreen(
    clipId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ClipDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(clipId) { viewModel.load(clipId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }

    var showNotesEditor by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        state.clip?.let {
                            haptics.medium()
                            viewModel.togglePin(it)
                        }
                    }) {
                        Icon(
                            imageVector = if (state.clip?.isPinned == true)
                                Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (state.clip?.isPinned == true)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val clip = state.clip
        if (clip == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        // Locked and not yet unlocked this session → show locked state
        if (clip.isLocked && !state.unlocked) {
            LockedScreen(
                onUnlock = {
                    promptUnlock(
                        activity = context.findActivity(),
                        viewModel = viewModel,
                        clip = clip
                    )
                },
                onBack = onBack,
                padding = padding
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(Motion.Long)) +
                    expandVertically(animationSpec = tween(Motion.Long)),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (clip.isPinned)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TypeBadge(clip.type)
                            Text(
                                text = formatDate(clip.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (clip.isLocked) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = clip.content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailAction(
                    label = if (copied) "Copied" else "Copy",
                    icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    highlight = copied,
                    onClick = {
                        haptics.light()
                        copyToClipboard(context, clip.content)
                        copied = true
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailAction(
                    label = "Transform",
                    icon = Icons.Outlined.Transform,
                    onClick = {
                        haptics.light()
                        showTransformSheet = true
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailAction(
                    label = "Queue",
                    icon = Icons.Outlined.Queue,
                    onClick = {
                        haptics.medium()
                        viewModel.addToQueue()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailAction(
                    label = if (clip.isLocked) "Unlock" else "Lock",
                    icon = if (clip.isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    highlight = clip.isLocked,
                    onClick = {
                        haptics.medium()
                        if (clip.isLocked) {
                            promptUnlock(
                                activity = context.findActivity(),
                                viewModel = viewModel,
                                clip = clip
                            )
                        } else {
                            viewModel.toggleLock(clip)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailAction(
                    label = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        haptics.light()
                        shareIntent(context, clip.content)
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailAction(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    onClick = {
                        haptics.heavy()
                        viewModel.delete(clip) { onDeleted() }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            DetailMetaRow(label = "Characters", value = "${clip.content.length}")
            DetailMetaRow(label = "Words", value = "${clip.content.split("\\s+".toRegex()).size}")
            if (clip.sourceLabel != null) {
                DetailMetaRow(label = "Source", value = clip.sourceLabel)
            }

            Spacer(Modifier.height(16.dp))

            NotesSection(
                notes = clip.notes,
                isLocked = clip.isLocked,
                onEdit = { showNotesEditor = true },
                onToggleLock = {
                    if (clip.isLocked) {
                        viewModel.toggleLock(clip)
                    } else {
                        showNotesEditor = false
                        viewModel.toggleLock(clip)
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    if (showTransformSheet) {
        TransformationBottomSheet(
            text = state.clip?.content.orEmpty(),
            onDismiss = { showTransformSheet = false },
            onReplace = { newText ->
                viewModel.replaceContent(newText)
                showTransformSheet = false
            },
            onCopyToClipboard = { newText ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("clip", newText))
                showTransformSheet = false
            }
        )
    }

    if (showNotesEditor && state.clip != null) {
        NotesEditorDialog(
            initialNotes = state.clip?.notes.orEmpty(),
            onDismiss = { showNotesEditor = false },
            onSave = { newNotes ->
                viewModel.setNotes(newNotes)
                showNotesEditor = false
            }
        )
    }
}

private fun promptUnlock(
    activity: FragmentActivity?,
    viewModel: ClipDetailViewModel,
    clip: com.clipvault.manager.domain.model.Clip
) {
    if (activity == null) {
        // Non-Activity context (e.g. preview): unlock without prompt
        viewModel.unlock(clip) {}
        return
    }
    if (!viewModel.biometricManager.canAuthenticate(activity)) {
        // No biometrics available: still allow access (graceful fallback)
        viewModel.unlock(clip) {}
        return
    }
    viewModel.biometricManager.prompt(
        activity = activity,
        title = "Unlock clip",
        subtitle = "Authenticate to view this clip's content.",
        onSuccess = { viewModel.unlock(clip) {} },
        onFailure = { /* keep locked */ }
    )
}

private fun Context.findActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun LockedScreen(
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Locked clip",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Authenticate to view this clip's content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onUnlock) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Unlock")
        }
    }
}

@Composable
private fun NotesSection(
    notes: String?,
    isLocked: Boolean,
    onEdit: () -> Unit,
    onToggleLock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLocked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Text(if (notes.isNullOrBlank()) "Add" else "Edit")
                }
            }
            if (!notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add context or reminders. Markdown supported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotesEditorDialog(
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialNotes) { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("# Heading\n**Bold**, *italic*, `code`") },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text != initialNotes || initialNotes.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransformationBottomSheet(
    text: String,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var preview by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<TextTransformer.Type?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Transform",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            Text(
                "Pick a transformation and copy or replace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
            TextTransformer.Type.entries.forEach { type ->
                val isSelected = selected == type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = type
                            when (val result = type.transform(text)) {
                                is TransformationResult.Success -> {
                                    preview = result.text
                                    error = null
                                }
                                is TransformationResult.Failure -> {
                                    error = result.error
                                    preview = null
                                }
                            }
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        type.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            if (preview != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = preview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { onCopyToClipboard(preview!!) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy") }
                    TextButton(
                        onClick = { onReplace(preview!!) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Replace") }
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = when {
        destructive -> MaterialTheme.colorScheme.errorContainer
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        highlight -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = container,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = onContainer)
        }
    }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

private fun copyToClipboard(context: Context, content: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("clip", content))
}

private fun shareIntent(context: Context, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}