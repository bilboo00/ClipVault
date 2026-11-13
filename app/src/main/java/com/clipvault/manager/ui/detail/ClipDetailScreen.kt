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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.clipvault.manager.domain.TextTransformer
import com.clipvault.manager.domain.TransformationResult
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.ui.components.AnimatedCopyButton
import com.clipvault.manager.ui.components.AnimatedToggleIcon
import com.clipvault.manager.ui.components.OrganizeSheet
import com.clipvault.manager.ui.components.StackedSnackbarHost
import com.clipvault.manager.ui.components.TypeBadge
import com.clipvault.manager.ui.components.rememberStackedSnackbarHostState
import com.clipvault.manager.ui.theme.Spacing
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
    val clip = state.clip
    val tagIds = state.tagIds
    val tags = state.tags
    val collectionIds = state.collectionIds
    val collections = state.collections
    val loading = state.loading
    val unlocked = state.unlocked
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
        // collectLatest (see HomeScreen): a sequential collector would queue
        // snackbars behind each other and delay the Undo affordance.
        viewModel.events.collectLatest { event ->
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
        containerColor = MaterialTheme.colorScheme.background,
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
                        clip?.let {
                            haptics.medium()
                            viewModel.toggleFavorite(it)
                        }
                    }) {
                        AnimatedToggleIcon(
                            active = clip?.isFavorite == true,
                            activeIcon = Icons.Filled.Favorite,
                            inactiveIcon = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (clip?.isFavorite == true)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        clip?.let {
                            haptics.medium()
                            viewModel.togglePin(it)
                        }
                    }) {
                        AnimatedToggleIcon(
                            active = clip?.isPinned == true,
                            activeIcon = Icons.Filled.PushPin,
                            inactiveIcon = Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (clip?.isPinned == true)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
        if (clip == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                if (loading) {
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
        if (clip.isLocked && !unlocked) {
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
                .padding(horizontal = Spacing.Xl, vertical = Spacing.Sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                TypeBadge(clip.type)
                Text(
                    text = formatDate(clip.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (clip.isPinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (clip.isLocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = clip.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Single scrollable row of tonal action pills instead of the
            // old 8-tile grid — quieter, and it scales without reflowing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                ActionPill(
                    label = if (copied) "Copied" else "Copy",
                    icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    onClick = {
                        haptics.light()
                        ClipUtils.copyToClipboard(context, clip.content, clip.imageUri)
                        viewModel.recordUsage()
                        copied = true
                    }
                )
                ActionPill(
                    label = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        haptics.light()
                        ClipUtils.shareClip(context, clip.content, clip.imageUri)
                    }
                )
                ActionPill(
                    label = "Transform",
                    icon = Icons.Outlined.Transform,
                    onClick = {
                        haptics.light()
                        showTransformSheet = true
                    }
                )
                ActionPill(
                    label = "Queue",
                    icon = Icons.Outlined.Queue,
                    onClick = {
                        haptics.medium()
                        viewModel.addToQueue()
                    }
                )
                ActionPill(
                    label = if (clip.isLocked) "Unlock" else "Lock",
                    icon = if (clip.isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
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
                    }
                )
                ActionPill(
                    label = "Organize",
                    icon = Icons.Outlined.Sell,
                    onClick = {
                        haptics.light()
                        showOrganizeSheet = true
                    }
                )
                ActionPill(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    isDestructive = true,
                    onClick = {
                        haptics.heavy()
                        showDeleteConfirm = true
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Metadata card: characters / words / source — grouped so the
            // whole thing reads as one block instead of loose label-value
            // rows floating in space.
            MetadataCard(
                rows = buildList {
                    add(MetaItem("Characters", "${clip.content.length}", Icons.Outlined.TextFields))
                    add(
                        MetaItem(
                            "Words",
                            remember(clip.content) {
                                clip.content.trim().split(Regex("\\s+"))
                                    .count { it.isNotEmpty() }.toString()
                            },
                            Icons.AutoMirrored.Outlined.Notes
                        )
                    )
                    if (clip.sourceLabel != null) {
                        add(MetaItem("Source", clip.sourceLabel, Icons.Outlined.Source))
                    }
                }
            )

            if (clip.sourceLabel != null || clip.hasExpiration || clip.hasUseLimit) {
                Spacer(Modifier.height(12.dp))
            }
            val assigned = remember(tagIds, tags, collectionIds, collections) {
                tags.filter { it.id in tagIds }.map { it.name } +
                    collections.filter { it.id in collectionIds }.map { it.name }
            }
            if (assigned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                OrganizedCard(names = assigned)
            }

            Spacer(Modifier.height(16.dp))

            // Expiry controls grouped into one connected card instead of
            // two floating ones.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column {
                    TempClipRow(
                        label = "Expires",
                        value = clip.expiresAt?.let { "in ${formatRemaining(it, now)}" } ?: "Never",
                        icon = Icons.Outlined.Schedule,
                        onPick = { showExpirationDialog = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.Lg),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    TempClipRow(
                        label = "Use limit",
                        value = clip.useLimit?.let { "${clip.useCount}/${clip.useLimit} uses" } ?: "Unlimited",
                        icon = Icons.Outlined.Timer,
                        onPick = { showUseLimitDialog = true }
                    )
                }
            }

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
                        clip?.let { viewModel.delete(it) }
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
            text = clip?.content.orEmpty(),
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

    if (showNotesEditor && clip != null) {
        NotesEditorDialog(
            initialNotes = clip?.notes.orEmpty(),
            onDismiss = { showNotesEditor = false },
            onSave = { newNotes ->
                viewModel.setNotes(newNotes)
                showNotesEditor = false
            }
        )
    }

    if (showOrganizeSheet) {
        OrganizeSheet(
            tags = tags,
            collections = collections,
            selectedTagIds = tagIds,
            selectedCollectionIds = collectionIds,
            onToggleTag = viewModel::toggleTag,
            onToggleCollection = viewModel::toggleCollection,
            onDismiss = { showOrganizeSheet = false }
        )
    }

    if (showExpirationDialog) {
        ExpirationPickerDialog(
            current = clip?.expiresAt,
            onDismiss = { showExpirationDialog = false },
            onPick = { expiresAt ->
                viewModel.setExpiration(expiresAt)
                showExpirationDialog = false
            }
        )
    }

    if (showUseLimitDialog) {
        UseLimitPickerDialog(
            current = clip?.useLimit,
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
            .padding(Spacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft brand bloom, matching the full-screen Lock and the hero
            // cards on Home / Insights.
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(Modifier.height(Spacing.Xxl))
        Text(
            "Locked clip",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.Sm))
        Text(
            "Authenticate to view this clip's content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.Xxl))
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null)
            Spacer(Modifier.width(Spacing.Sm))
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(Spacing.Sm))
                Text(
                    "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLocked) {
                    Spacer(Modifier.width(Spacing.Sm))
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .padding(horizontal = 24.dp, vertical = Spacing.CardPadding),
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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

/**
 * Single row of metadata inside a [MetadataCard]. Icon + label on the left,
 * value on the right, both vertically centred. 22.dp icon keeps the row
 * compact without losing the affordance.
 */
@Composable
private fun DetailMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One labelled metadata row inside a card. Icon + label on the left, value
 * on the right; rows are separated by a hairline divider.
 */
private data class MetaItem(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun MetadataCard(rows: List<MetaItem>) {
    if (rows.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            rows.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Lg, vertical = Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Md))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.Lg),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/**
 * Pill row listing the tags / collections the clip belongs to. Each name
 * is a tonal pill so they read as items, not a comma-soup string.
 */
@Composable
private fun OrganizedCard(names: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(Spacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Sell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.Md))
                Text(
                    text = "Organized under",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(Spacing.Sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                names.forEach { name ->
                    AssistChip(
                        onClick = {},
                        label = { Text(name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TempClipRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .semantics { role = Role.Button }
            .minimumInteractiveComponentSize()
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.Md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Spacing.Xs))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Tonal action pill for the detail action row — filled `surfaceContainer`
 * with no outline, destructive variant in `errorContainer`, matching the
 * filter pills on Home.
 */
@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isDestructive) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(role = Role.Button, onClick = onClick)
            .height(32.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
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