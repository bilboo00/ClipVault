package com.clipvault.manager.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.BuildConfig
import com.clipvault.manager.ui.theme.AppPalette
import com.clipvault.manager.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var showBubbleDialog by remember { mutableStateOf(false) }
    var showRetentionSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf(com.clipvault.manager.data.export.ExportFormat.JSON) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Debug-only crash reporter surface: appears only when something has been
    // captured. Tap copies the newest stack trace so it can be pasted into a
    // chat. Hoisted here because the About group is built lazily.
    var crashReports by remember {
        mutableStateOf(com.clipvault.manager.util.CrashReporter.pendingReports(context))
    }
    LaunchedEffect(Unit) {
        crashReports = com.clipvault.manager.util.CrashReporter.pendingReports(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isExporting = true
                try {
                    val clips = viewModel.exportAllClips()
                    val content = com.clipvault.manager.data.export.ClipExporter.export(clips, selectedExportFormat)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(content.toByteArray())
                        }
                    }
                    exportMessage = "Exported ${clips.size} clips as ${selectedExportFormat.extension.uppercase()}"
                } catch (e: Exception) {
                    exportMessage = "Export failed: ${e.message}"
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val clips = withContext(Dispatchers.IO) {
                        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().readText()
                        } ?: throw Exception("Could not read file")
                        if (raw.trimStart().startsWith("[")) {
                            com.clipvault.manager.data.export.ClipJsonExporter.importFromJson(raw)
                        } else if (raw.trimStart().startsWith("{")) {
                            com.clipvault.manager.data.export.ClipJsonExporter.importFromJson(raw)
                        } else {
                            throw Exception("Only JSON exports are supported for import.")
                        }
                    }
                    val count = viewModel.importClips(clips)
                    exportMessage = "Imported $count clips"
                } catch (e: Exception) {
                    exportMessage = "Import failed: ${e.message}"
                }
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.Md,
                end = Spacing.Md,
                top = Spacing.Sm,
                bottom = 96.dp
            )
        ) {
            item(key = "hero", contentType = "hero") { PreferencesHero() }

            // ── Capture ─────────────────────────────────────────────────
            item(key = "capture_label", contentType = "label") { SectionLabel("Capture") }
            item(key = "capture_group", contentType = "group") {
                SettingsGroup {
                    ToggleRow(
                        icon = Icons.Outlined.ContentPaste,
                        title = "Clipboard monitoring",
                        subtitle = "Save everything you copy while using your phone.",
                        checked = state.monitoringEnabled,
                        onCheckedChange = viewModel::setMonitoring
                    )
                    ToggleRow(
                        icon = Icons.Outlined.BubbleChart,
                        title = "Floating bubble",
                        subtitle = bubbleSubtitle(context),
                        checked = state.bubbleEnabled,
                        tint = MaterialTheme.colorScheme.secondary,
                        onCheckedChange = { requested ->
                            if (requested && !canDrawOverlays(context)) {
                                showBubbleDialog = true
                            } else {
                                viewModel.setBubbleEnabled(requested)
                            }
                        }
                    )
                    SettingsSubCard(
                        label = "More ways to capture (2)",
                        modifier = Modifier.padding(
                            horizontal = Spacing.Lg,
                            vertical = Spacing.Sm
                        )
                    ) {
                        SubRow(
                            icon = Icons.Outlined.AccessibilityNew,
                            title = "Accessibility service",
                            subtitle = "Optional. Capture copies in any app.",
                            statusLabel = "Open",
                            onClick = {
                                runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) }
                            }
                        )
                        SubRow(
                            icon = Icons.Outlined.Widgets,
                            title = "Quick Settings tile",
                            subtitle = "Pull down the shade → edit → drag to the bar.",
                            iconTint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // ── Privacy & security ──────────────────────────────────────
            item(key = "privacy_label", contentType = "label") { SectionLabel("Privacy & security") }
            item(key = "privacy_group", contentType = "group") {
                SettingsGroup {
                    ToggleRow(
                        icon = Icons.Outlined.VisibilityOff,
                        title = "Mask sensitive content",
                        subtitle = "Hide clip text in notifications, widget, and search.",
                        checked = state.maskSensitiveContent,
                        onCheckedChange = viewModel::setMaskSensitiveContent
                    )
                    DropdownRow(
                        icon = Icons.Outlined.AutoDelete,
                        title = "Auto-delete clips",
                        subtitle = "Older clips are removed automatically.",
                        chipLabel = retentionChipLabel(state.retentionDays),
                        onClick = { showRetentionSheet = true }
                    )
                    ToggleRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Require biometric to open",
                        subtitle = "Lock the app behind biometric / device credential.",
                        checked = state.requireBiometric,
                        tint = MaterialTheme.colorScheme.secondary,
                        // Re-prompt biometric on every change so an attacker
                        // can't silently flip the lock off (or on) while the
                        // owner is away from the device. Without this the
                        // toggle was a single tap to disable the gate.
                        onCheckedChange = { requested ->
                            val activity = context.findActivity()
                            if (activity == null ||
                                !viewModel.biometricManager.canAuthenticate(activity)
                            ) {
                                // No credential set on the device — never
                                // silently allow disabling. Surface the
                                // requirement so the user knows why.
                                android.widget.Toast.makeText(
                                    context,
                                    "Set up a screen lock or biometrics first.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                return@ToggleRow
                            }
                            viewModel.biometricManager.prompt(
                                activity = activity,
                                title = if (requested) "Enable app lock"
                                        else "Disable app lock",
                                subtitle = "Authenticate to confirm this change.",
                                onSuccess = { viewModel.setRequireBiometric(requested) },
                                onFailure = { msg ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Authentication failed: $msg",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onCancel = { /* leave toggle where it was */ }
                            )
                        }
                    )
                }
            }

            // ── Appearance ──────────────────────────────────────────────
            item(key = "appearance_label", contentType = "label") { SectionLabel("Appearance") }
            item(key = "appearance_group", contentType = "group") {
                SettingsGroup {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ToggleRow(
                            icon = Icons.Outlined.Wallpaper,
                            title = "Dynamic system colors",
                            subtitle = "Derive the palette from your wallpaper (Android 12+).",
                            checked = state.dynamicColors,
                            onCheckedChange = { viewModel.setDynamicColors(it) },
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    DropdownRow(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = "Pick the look that suits you.",
                        chipLabel = themeLabel(state.themeMode),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showThemeSheet = true }
                    )
                    SettingsSubCard(
                        label = null,
                        modifier = Modifier.padding(
                            horizontal = Spacing.Lg,
                            vertical = Spacing.Sm
                        )
                    ) {
                        PalettePicker(
                            selectedId = state.palette,
                            enabled = !state.dynamicColors,
                            onSelect = { viewModel.setPalette(it) }
                        )
                    }
                }
            }

            // ── Library ─────────────────────────────────────────────────
            item(key = "library_label", contentType = "label") { SectionLabel("Library") }
            item(key = "library_group", contentType = "group") {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Sell,
                        title = "Tags",
                        subtitle = "Organize clips with custom labels.",
                        onClick = { onNavigate(com.clipvault.manager.ui.nav.Route.Tags.path) }
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Folder,
                        title = "Collections",
                        subtitle = "Group related clips into folders.",
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigate(com.clipvault.manager.ui.nav.Route.Collections.path) }
                    )
                    SettingsRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = "Find duplicates",
                        subtitle = "Merge identical clips to keep history tidy.",
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = { showDuplicatesDialog = true }
                    )
                }
            }

            // ── Data & backups ──────────────────────────────────────────
            item(key = "data_label", contentType = "label") { SectionLabel("Data & backups") }
            item(key = "data_group", contentType = "group") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    ActionPill(
                        icon = Icons.Outlined.Archive,
                        label = "Export history",
                        trailingIcon = Icons.Outlined.Download,
                        loading = isExporting,
                        onClick = { if (!isExporting) showExportFormatSheet = true }
                    )
                    ActionPill(
                        icon = Icons.Outlined.Unarchive,
                        label = "Import history",
                        trailingIcon = Icons.Outlined.Upload,
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    )
                    if (exportMessage != null) {
                        Text(
                            text = exportMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.Xs)
                        )
                    }
                    DangerPill(
                        icon = Icons.Outlined.DeleteSweep,
                        label = "Delete all clips",
                        trailingLabel = "Pinned kept",
                        onClick = { showClearDialog = true }
                    )
                }
            }

            // ── About ───────────────────────────────────────────────────
            item(key = "about_label", contentType = "label") { SectionLabel("About") }
            item(key = "about_group", contentType = "group") {
                SettingsGroup {
                    if (crashReports.isNotEmpty()) {
                        SettingsRow(
                            icon = Icons.Outlined.BugReport,
                            title = "Debug crash logs (${crashReports.size})",
                            subtitle = "Tap to copy the newest stack trace.",
                            onClick = {
                                val text = com.clipvault.manager.util.CrashReporter.latestReportText(context)
                                    ?: "No report content"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("ClipVault crash log", text.take(90_000))
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "Newest crash log copied — paste it to the developer",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "ClipVault",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        tint = if (crashReports.isNotEmpty()) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        onClick = { showAboutDialog = true }
                    )
                    SettingsRow(
                        icon = Icons.Outlined.RestartAlt,
                        title = "Replay setup guide",
                        subtitle = "Walk through the onboarding flow again.",
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = { viewModel.resetOnboarding() }
                    )
                }
            }

            // ── Footer ──────────────────────────────────────────────────
            item(key = "footer", contentType = "footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Xxxl, bottom = Spacing.Sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Spacing.Xs))
                        Text(
                            text = "ClipVault v${BuildConfig.VERSION_NAME} · Material 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(Spacing.Xxs))
                    Text(
                        text = "All data stays on your device · No accounts · No tracking",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────

    if (showBubbleDialog) {
        AlertDialog(
            onDismissRequest = { showBubbleDialog = false },
            icon = { Icon(Icons.Outlined.BubbleChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Overlay permission") },
            text = { Text("To show the floating bubble, Android requires the \"draw over other apps\" permission.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { context.startActivity(viewModel.overlayPermissionIntent()) }
                    showBubbleDialog = false
                }) { Text("Grant") }
            },
            dismissButton = {
                TextButton(onClick = { showBubbleDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete all clips?") },
            text = { Text("This permanently removes every saved clip from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showClearDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = { Text("ClipVault") },
            text = {
                Column {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.Sm))
                    Text(
                        "A simple clipboard history app. Everything you copy is saved locally — no account, no cloud, no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Done") } }
        )
    }

    if (showDuplicatesDialog) {
        val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
        LaunchedEffect(showDuplicatesDialog) {
            if (showDuplicatesDialog) viewModel.refreshDuplicates()
        }
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Find duplicates") },
            text = {
                if (duplicates.isEmpty()) {
                    Text("No duplicate clips found — nice and tidy.")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Each group shares the same content. Merging keeps the newest clip (pinned first) and folds tags, collections and use counts into it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.Md))
                        duplicates.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${group.count} copies",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.mergeDuplicate(group.keepId, group.content)
                                }) { Text("Merge") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDuplicatesDialog = false }) { Text("Done") } }
        )
    }

    if (showRetentionSheet) {
        RetentionPickerSheet(
            selected = state.retentionDays,
            onSelect = { viewModel.setRetention(it); showRetentionSheet = false },
            onDismiss = { showRetentionSheet = false }
        )
    }

    if (showThemeSheet) {
        ThemePickerSheet(
            selected = state.themeMode,
            onSelect = { viewModel.setTheme(it); showThemeSheet = false },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showExportFormatSheet) {
        ExportFormatPickerSheet(
            selected = selectedExportFormat,
            onDismiss = { showExportFormatSheet = false },
            onSelect = { format ->
                if (isExporting) return@ExportFormatPickerSheet
                selectedExportFormat = format
                showExportFormatSheet = false
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                exportLauncher.launch("clipvault_export_$timestamp.${format.extension}")
            }
        )
    }
}

// ── Layout primitives ─────────────────────────────────────────────

/** Hero banner: primary tile + title + one-line promise. */
@Composable
private fun PreferencesHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            // Soft decorative bloom in the corner — a radial gradient instead
            // of Modifier.blur so it renders identically below API 31.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 28.dp)
                    .size(96.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Row(
                modifier = Modifier.padding(Spacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.Md))
                Column {
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Tailor capture, privacy & style",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/** Uppercase periwinkle section label, per the design export. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.Sm,
            top = Spacing.Xl,
            bottom = Spacing.Sm
        )
    )
}

/** Borderless tonal group card holding the settings rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.Sm),
            content = content
        )
    }
}

/** 40dp icon circle used by every settings row. */
@Composable
private fun SettingBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    // Use `toggleable` on the row and let the Switch react purely to the
    // hoisted `checked` state. The previous `.clickable { onCheckedChange(!checked) }`
    // combined with the Switch's own `onCheckedChange` could fire twice on a
    // single tap (row click → toggle, then Switch click → toggle again),
    // which both flipped the switch back and emitted two DataStore writes
    // per tap — the visible cause of the "stuttery" feel on every row.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingBadge(icon, tint)
        Spacer(Modifier.width(Spacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.Sm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingBadge(icon, tint)
        Spacer(Modifier.width(Spacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Row whose trailing control is a tappable value chip (auto-delete, theme). */
@Composable
private fun DropdownRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    chipLabel: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.secondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingBadge(icon, tint)
        Spacer(Modifier.width(Spacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.Sm))
        ValueChip(chipLabel)
    }
}

/** 36dp rounded value chip with a chevron. */
@Composable
private fun ValueChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Inset sub-card used to tuck secondary rows inside a group card. */
@Composable
private fun SettingsSubCard(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(Spacing.Md)) {
            if (label != null) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.Xs))
            }
            content()
        }
    }
}

/** Compact icon + text row inside a [SettingsSubCard]. */
@Composable
private fun SubRow(
    icon: ImageVector,
    title: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    statusLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (statusLabel != null) {
            Spacer(Modifier.width(Spacing.Sm))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Interactive accent-palette picker — one swatch per palette, checkmark on
 * the active one, palette name trailing. Dimmed and frozen while dynamic
 * (wallpaper) colours are enabled, since those bypass the palette entirely.
 */
@Composable
private fun PalettePicker(
    selectedId: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val active = AppPalette.fromId(selectedId)
    val dim = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPalette.entries.forEach { palette ->
                val isSelected = palette.id == active.id
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .alpha(dim)
                        .clip(CircleShape)
                        .background(palette.light.primary)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(palette.id) }
                        )
                        .semantics {
                            contentDescription = "${palette.label} palette"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.light.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.Sm))
        Text(
            text = if (enabled) "${active.label} (Active)" else active.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/** Full-width pill action, optionally showing an inline progress spinner. */
@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    loading: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Spacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(Spacing.Sm))
            Text(
                text = if (loading) "Exporting…" else label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null && !loading) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Destructive pill with a small trailing note pill, per the design export. */
@Composable
private fun DangerPill(
    icon: ImageVector,
    label: String,
    trailingLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Spacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            ) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xxs)
                )
            }
        }
    }
}

// ── Bottom sheets ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionPickerSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Auto-delete after",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            Text(
                "Older clips are removed automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
            listOf(0, 7, 30, 90, 365).forEach { days ->
                SheetOption(
                    label = when (days) {
                        0 -> "Never"
                        365 -> "1 year"
                        else -> "$days days"
                    },
                    selected = selected == days,
                    onClick = { onSelect(days) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Theme",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            Text(
                "Pick the look that suits you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
            listOf(
                0 to "Follow system",
                1 to "Light",
                2 to "Dark",
                3 to "AMOLED Black"
            ).forEach { (value, label) ->
                SheetOption(
                    label = label,
                    selected = selected == value,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        AnimatedVisibility(visible = selected, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────

@Composable
private fun bubbleSubtitle(context: Context): String {
    val overlayGranted = canDrawOverlays(context)
    return if (overlayGranted) "Tap the bubble to save the current clipboard."
    else "Tap to grant the overlay permission."
}

private fun canDrawOverlays(context: Context): Boolean =
    Settings.canDrawOverlays(context)

private fun retentionChipLabel(days: Int): String = when (days) {
    0 -> "Never"
    365 -> "1 year"
    else -> "$days days"
}

private fun themeLabel(mode: Int): String = when (mode) {
    0 -> "Follow system"
    1 -> "Light"
    2 -> "Dark"
    3 -> "AMOLED Black"
    else -> "Follow system"
}

/** Walk the ContextWrapper chain looking for a hosting FragmentActivity. */
private fun Context.findActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatPickerSheet(
    selected: com.clipvault.manager.data.export.ExportFormat,
    onDismiss: () -> Unit,
    onSelect: (com.clipvault.manager.data.export.ExportFormat) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Export format",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            Text(
                "Pick how to encode your export.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
            com.clipvault.manager.data.export.ExportFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(format) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        format.extension.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected == format) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected == format) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(visible = selected == format, enter = fadeIn(), exit = fadeOut()) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
