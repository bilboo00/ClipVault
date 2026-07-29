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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clipvault.manager.BuildConfig
import kotlinx.coroutines.launch

private data class IconAccent(val bg: Color, val tint: Color)

private val Indigo = IconAccent(Color(0xFFEEF2FF), Color(0xFF4F46E5))
private val Amber = IconAccent(Color(0xFFFEF3C7), Color(0xFFD97706))
private val Blue = IconAccent(Color(0xFFDBEAFE), Color(0xFF2563EB))
private val Violet = IconAccent(Color(0xFFEDE9FE), Color(0xFF7C3AED))
private val Green = IconAccent(Color(0xFFDCFCE7), Color(0xFF16A34A))
private val Pink = IconAccent(Color(0xFFFCE7F3), Color(0xFFDB2777))
private val Slate = IconAccent(Color(0xFFF1F5F9), Color(0xFF475569))
private val Red = IconAccent(Color(0xFFFEE2E2), Color(0xFFDC2626))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var showBubbleDialog by remember { mutableStateOf(false) }
    var showRetentionSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf(com.clipvault.manager.data.export.ExportFormat.JSON) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val clips = viewModel.exportAllClips()
                    val content = com.clipvault.manager.data.export.ClipExporter.export(clips, selectedExportFormat)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray())
                    }
                    exportMessage = "Exported ${clips.size} clips as ${selectedExportFormat.extension.uppercase()}"
                } catch (e: Exception) {
                    exportMessage = "Export failed: ${e.message}"
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
                    val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: throw Exception("Could not read file")
                    val clips = if (raw.trimStart().startsWith("[")) {
                        com.clipvault.manager.data.export.ClipJsonExporter.importFromJson(raw)
                    } else if (raw.trimStart().startsWith("{")) {
                        com.clipvault.manager.data.export.ClipJsonExporter.importFromJson(raw)
                    } else {
                        throw Exception("Only JSON exports are supported for import.")
                    }
                    val count = viewModel.importClips(clips)
                    exportMessage = "Imported $count clips"
                } catch (e: Exception) {
                    exportMessage = "Import failed: ${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // ── Monitoring ──────────────────────────────────────────────
            item { SectionHeader("Monitoring") }
            item {
                SettingsCard {
                    ToggleRow(
                        accent = Indigo,
                        icon = Icons.Outlined.ContentPaste,
                        title = "Clipboard monitoring",
                        subtitle = "Save everything you copy while using your phone.",
                        checked = state.monitoringEnabled,
                        onCheckedChange = viewModel::setMonitoring
                    )
                }
            }

            // ── Quick access ────────────────────────────────────────────
            item { SectionHeader("Quick access") }
            item {
                SettingsCard {
                    ToggleRow(
                        accent = Amber,
                        icon = Icons.Outlined.BubbleChart,
                        title = "Floating bubble",
                        subtitle = bubbleSubtitle(context),
                        checked = state.bubbleEnabled,
                        onCheckedChange = { requested ->
                            if (requested && !canDrawOverlays(context)) {
                                showBubbleDialog = true
                            } else {
                                viewModel.setBubbleEnabled(requested)
                            }
                        }
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Blue,
                        icon = Icons.Outlined.Widgets,
                        title = "Quick Settings tile",
                        subtitle = "Pull down the shade → edit → drag to the bar."
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Violet,
                        icon = Icons.Outlined.AccessibilityNew,
                        title = "Accessibility service",
                        subtitle = "Optional. Capture copies in any app.",
                        onClick = { runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) } }
                    )
                }
            }

            // ── Organize ─────────────────────────────────────────────────
            item { SectionHeader("Organize") }
            item {
                SettingsCard {
                    ChevronRow(
                        accent = Indigo,
                        icon = Icons.Outlined.Sell,
                        title = "Tags",
                        subtitle = "Organize clips with custom labels."
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Violet,
                        icon = Icons.Outlined.Folder,
                        title = "Collections",
                        subtitle = "Group related clips into folders."
                    )
                }
            }

            // ── Cleanup ─────────────────────────────────────────────────
            item { SectionHeader("Cleanup") }
            item {
                SettingsCard {
                    ChevronRow(
                        accent = Green,
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Auto-delete clips",
                        subtitle = retentionLabel(state.retentionDays),
                        onClick = { showRetentionSheet = true }
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Pink,
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = themeLabel(state.themeMode),
                        onClick = { showThemeSheet = true }
                    )
                }
            }

            // ── Data ──────────────────────────────────────────────────
            item { SectionHeader("Data") }
            item {
                SettingsCard {
                    ChevronRow(
                        accent = Blue,
                        icon = Icons.Outlined.FileUpload,
                        title = "Export history",
                        subtitle = "Save all clips as ${selectedExportFormat.extension.uppercase()}.",
                        onClick = {
                            showExportFormatSheet = true
                        }
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Green,
                        icon = Icons.Outlined.FileDownload,
                        title = "Import history",
                        subtitle = "Restore clips from a JSON backup.",
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }

            // ── About ───────────────────────────────────────────────────
            item { SectionHeader("About") }
            item {
                SettingsCard {
                    ChevronRow(
                        accent = Slate,
                        icon = Icons.Outlined.Info,
                        title = "Clipboard Manager",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        onClick = { showAboutDialog = true }
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Amber,
                        icon = Icons.Outlined.RestartAlt,
                        title = "Replay setup guide",
                        subtitle = "Walk through the onboarding flow again.",
                        onClick = { viewModel.resetOnboarding() }
                    )
                    SettingsDivider()
                    ChevronRow(
                        accent = Red,
                        icon = Icons.Outlined.DeleteForever,
                        title = "Delete all clips",
                        subtitle = "Removes every saved entry. Pinned items are kept.",
                        destructive = true,
                        onClick = { showClearDialog = true }
                    )
                }
            }

            // ── Footer ──────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "All data stays on your device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "No accounts · No tracking · No cloud",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────

    if (showBubbleDialog) {
        AlertDialog(
            onDismissRequest = { showBubbleDialog = false },
            icon = { Icon(Icons.Outlined.BubbleChart, contentDescription = null, tint = Amber.tint) },
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
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = Red.tint) },
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
            icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = Slate.tint) },
            title = { Text("Clipboard Manager") },
            text = {
                Column {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
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

    if (showRetentionSheet) {
        RetentionPickerSheet(
            selected = state.retentionDays,
            onSelect = { viewModel.setRetention(it); showRetentionSheet = false }
        )
    }

    if (showThemeSheet) {
        ThemePickerSheet(
            selected = state.themeMode,
            onSelect = { viewModel.setTheme(it); showThemeSheet = false }
        )
    }

    if (showExportFormatSheet) {
        ExportFormatPickerSheet(
            selected = selectedExportFormat,
            onSelect = { format ->
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        thickness = 1.dp
    )
}

@Composable
private fun ToggleRow(
    accent: IconAccent,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(accent, icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ChevronRow(
    accent: IconAccent,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = clickableModifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(accent, icon, destructive = destructive)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
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

@Composable
private fun IconBadge(accent: IconAccent, icon: ImageVector, destructive: Boolean = false) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (destructive) Red.bg else accent.bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) Red.tint else accent.tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Bottom sheets ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionPickerSheet(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { /* handled by parent state */ },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface
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
    onSelect: (Int) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        Settings.canDrawOverlays(context) else true

private fun retentionLabel(days: Int): String = when (days) {
    0 -> "Never"
    7 -> "After 7 days"
    30 -> "After 30 days"
    90 -> "After 90 days"
    365 -> "After 1 year"
    else -> "After $days days"
}

private fun themeLabel(mode: Int): String = when (mode) {
    0 -> "Follow system"
    1 -> "Light"
    2 -> "Dark"
    3 -> "AMOLED Black"
    else -> "Follow system"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatPickerSheet(
    selected: com.clipvault.manager.data.export.ExportFormat,
    onSelect: (com.clipvault.manager.data.export.ExportFormat) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface
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
                        .background(
                            if (selected == format) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        format.extension.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected == format) FontWeight.SemiBold else FontWeight.Normal,
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