package com.clipvault.manager.ui.detail

import android.content.Context
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.clipvault.manager.domain.TextTransformer
import com.clipvault.manager.domain.TransformationResult
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.OrganizeSheet
import com.clipvault.manager.ui.components.StackedSnackbarHost
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.components.rememberStackedSnackbarHostState
import com.clipvault.manager.util.ClipUtils
import com.clipvault.manager.util.rememberDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipDetailScreen(
    clipId: Long,
    onBack: () -> Unit,
    viewModel: ClipDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(clipId) { viewModel.load(clipId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val snackbarHostState = rememberStackedSnackbarHostState()
    val formatDate = rememberDateTime()
    var copied by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showNotesEditor by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }
    var showOrganizeSheet by remember { mutableStateOf(false) }
    var showExpirationDialog by remember { mutableStateOf(false) }
    var showUseLimitDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Keep the expiry countdown fresh while the screen is visible (and the app
    // is foregrounded — the ticker must not run while backgrounded).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                now = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClipDetailEvent.Deleted -> {
                    snackbarHostState.show(
                        message = "Clip deleted",
                        actionLabel = "Undo"
                    )?.let { viewModel.undoDelete(event.entity) }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { StackedSnackbarHost(snackbarHostState) },
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
                            viewModel.toggleFavorite(it)
                        }
                    }) {
                        Icon(
                            imageVector = if (state.clip?.isFavorite == true)
                                Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (state.clip?.isFavorite == true)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                if (state.loading) {
                    Text("Loading…")
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Clip deleted", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "It no longer exists in your history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onBack) { Text("Go back") }
                    }
                }
            }
            return@Scaffold
        }

        // Locked and not yet unlocked this session → show locked state
        if (clip.isLocked && !state.unlocked) {
            // Auto-prompt the biometric on first composition of the locked
            // surface so the user lands on the prompt, not on a screen that
            // does nothing until they tap Unlock. The prompt is started only
            // while RESUMED (androidx.biometric requires a resumed host —
            // firing from composition crashed the process), and the flag is
            // keyed per clip so rotation doesn't re-prompt mid-session.
            var hasPromptedForClip by rememberSaveable(clip.id) { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(clip.id) {
                if (!hasPromptedForClip) {
                    hasPromptedForClip = true
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        val activity = context.findActivity()
                        if (activity != null &&
                            viewModel.biometricManager.canAuthenticate(activity)
                        ) {
                            promptUnlock(
                                activity = activity,
                                viewModel = viewModel,
                                clip = clip
                            )
                        }
                    }
                }
            }
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
                        ClipUtils.copyToClipboard(context, clip.content, clip.imageUri)
                        viewModel.recordUsage()
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
                DetailAction(
                    label = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        haptics.light()
                        ClipUtils.shareClip(context, clip.content, clip.imageUri)
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
                        val activity = context.findActivity()
                        // Both lock and unlock require the biometric prompt
                        // when a credential is available, so the symmetric
                        // surface matches: the lock button can't be used to
                        // silently re-lock notes the user just unlocked, and
                        // unlocking still gates on auth.
                        if (activity != null &&
                            viewModel.biometricManager.canAuthenticate(activity)
                        ) {
                            promptUnlock(
                                activity = activity,
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
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    onClick = {
                        haptics.heavy()
                        showDeleteConfirm = true
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailAction(
                    label = "Organize",
                    icon = Icons.Outlined.Sell,
                    onClick = {
                        haptics.light()
                        showOrganizeSheet = true
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            DetailMetaRow(label = "Characters", value = "${clip.content.length}")
            DetailMetaRow(
                label = "Words",
                value = remember(clip.content) {
                    clip.content.trim().split(Regex("\\s+")).count { it.isNotEmpty() }.toString()
                }
            )
            if (clip.sourceLabel != null) {
                DetailMetaRow(label = "Source", value = clip.sourceLabel)
            }
            val assigned = remember(state.tagIds, state.tags, state.collectionIds, state.collections) {
                state.tags.filter { it.id in state.tagIds }.map { it.name } +
                    state.collections.filter { it.id in state.collectionIds }.map { it.name }
            }
            if (assigned.isNotEmpty()) {
                DetailMetaRow(label = "Organized", value = assigned.joinToString(", "))
            }

            Spacer(Modifier.height(16.dp))

            TempClipRow(
                label = "Expires",
                value = clip.expiresAt?.let { "in ${formatRemaining(it, now)}" } ?: "Never",
                onPick = { showExpirationDialog = true }
            )
            Spacer(Modifier.height(8.dp))
            TempClipRow(
                label = "Use limit",
                value = clip.useLimit?.let { "${clip.useCount}/${clip.useLimit} uses" } ?: "Unlimited",
                onPick = { showUseLimitDialog = true }
            )

            Spacer(Modifier.height(16.dp))

            NotesSection(
                notes = clip.notes,
                isLocked = clip.isLocked,
                onEdit = { showNotesEditor = true },
                onToggleLock = {
                    // Both directions now route through the biometric prompt
                    // when a credential is available, so an attacker with
                    // physical access to an unlocked phone can't silently
                    // re-lock notes to hide tampering (and the symmetric
                    // "lock" path was previously unguarded by mistake).
                    val activity = context.findActivity()
                    if (activity != null &&
                        viewModel.biometricManager.canAuthenticate(activity)
                    ) {
                        promptUnlock(
                            activity = activity,
                            viewModel = viewModel,
                            clip = clip
                        )
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete clip?") },
            text = {
                Text(
                    "This clip will be removed from your history. You can undo right after deleting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        state.clip?.let { viewModel.delete(it) }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
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
                ClipUtils.copyToClipboard(context, newText)
                viewModel.recordUsage()
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

    if (showOrganizeSheet) {
        OrganizeSheet(
            tags = state.tags,
            collections = state.collections,
            selectedTagIds = state.tagIds,
            selectedCollectionIds = state.collectionIds,
            onToggleTag = viewModel::toggleTag,
            onToggleCollection = viewModel::toggleCollection,
            onDismiss = { showOrganizeSheet = false }
        )
    }

    if (showExpirationDialog) {
        ExpirationPickerDialog(
            current = state.clip?.expiresAt,
            onDismiss = { showExpirationDialog = false },
            onPick = { expiresAt ->
                viewModel.setExpiration(expiresAt)
                showExpirationDialog = false
            }
        )
    }

    if (showUseLimitDialog) {
        UseLimitPickerDialog(
            current = state.clip?.useLimit,
            onDismiss = { showUseLimitDialog = false },
            onPick = { limit ->
                viewModel.setUseLimit(limit)
                showUseLimitDialog = false
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
        // No activity to host a biometric prompt — never silently unlock,
        // otherwise a locked clip could be exposed via a non-Activity context.
        return
    }
    if (!viewModel.biometricManager.canAuthenticate(activity)) {
        // No biometrics available: still allow removing the lock (graceful fallback)
        viewModel.toggleLock(clip)
        return
    }
    // Pick the prompt copy based on which way the toggle is going so the
    // user understands why they're being asked to authenticate on a Lock
    // tap as well as an Unlock tap.
    val (title, subtitle) = if (clip.isLocked) {
        "Unlock clip" to "Authenticate to remove this clip's lock."
    } else {
        "Lock clip" to "Authenticate to lock this clip's notes."
    }
    viewModel.biometricManager.prompt(
        activity = activity,
        title = title,
        subtitle = subtitle,
        onSuccess = { viewModel.toggleLock(clip) },
        onFailure = { msg ->
            android.widget.Toast.makeText(
                activity,
                "Authentication failed: $msg",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        },
        onCancel = {
            // User dismissed the prompt — leave the clip in its current
            // state, no toast spam.
        }
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
            contentDescription = "Locked",
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
        Button(
            onClick = onUnlock,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
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
                    Icons.AutoMirrored.Outlined.NoteAdd,
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
                    "Add context or reminders.",
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
                enabled = text != initialNotes
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
                        .semantics { role = Role.Button }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    destructive: Boolean = false
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

@Composable
private fun TempClipRow(
    label: String,
    value: String,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExpirationPickerDialog(
    current: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit
) {
    val options = listOf(
        "Never" to null,
        "5 minutes" to (5 * 60_000L),
        "30 minutes" to (30 * 60_000L),
        "1 hour" to (60 * 60_000L),
        "12 hours" to (12 * 60 * 60_000L),
        "24 hours" to (24 * 60 * 60_000L),
        "7 days" to (7 * 24 * 60 * 60_000L)
    )
    val now = System.currentTimeMillis()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-delete") },
        text = {
            Column {
                Text(
                    "The clip is removed after the chosen time. Pinned clips are never auto-deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                options.forEach { (label, offset) ->
                    val expiresAt = offset?.let { now + it }
                    val isCurrent = current != null && offset != null &&
                        kotlin.math.abs((current - now) - offset) < 30_000
                    TextButton(
                        onClick = { onPick(expiresAt) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            if (isCurrent) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UseLimitPickerDialog(
    current: Int?,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit
) {
    val options = listOf<Pair<String, Int?>>(
        "Unlimited" to null,
        "1 use" to 1,
        "2 uses" to 2,
        "3 uses" to 3,
        "5 uses" to 5,
        "10 uses" to 10
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use limit") },
        text = {
            Column {
                Text(
                    "The clip is removed after being copied this many times. Pinned clips are never auto-deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                options.forEach { (label, limit) ->
                    TextButton(
                        onClick = { onPick(limit) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            if (current != null && current == limit) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatRemaining(expiresAt: Long, now: Long): String {
    val diff = expiresAt - now
    if (diff <= 0) return "now"
    val minutes = diff / 60_000
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}