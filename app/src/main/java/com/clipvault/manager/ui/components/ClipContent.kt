package com.clipvault.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.clipvault.manager.haptic.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun ClipContent(
    clip: com.clipvault.manager.domain.model.Clip,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit
) {
    var editedText by remember { mutableStateOf(clip.content) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (clip.isPinned) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: type badge + copy + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeBadge(clip.type)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            scope.launch { haptics.light() }
                            onSave(clip.id, normalizeText(editedText))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Editor field
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done
                ),
                label = { Text("Edit clip") },
                singleLine = false
            )

            // Smart format strip (type-aware)
            FormatButtons(
                text = editedText,
                clipType = clip.type,
                haptics = haptics,
                scope = scope,
                onTransform = { newText -> editedText = newText }
            )
        }
    }
}

@Composable
private fun FormatButtons(
    text: String,
    clipType: com.clipvault.manager.data.local.entity.ClipType,
    haptics: com.clipvault.manager.haptic.Haptics,
    scope: kotlinx.coroutines.CoroutineScope,
    onTransform: (String) -> Unit
) {
    val chips = getFormatChips(clipType)
    if (chips.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { chip ->
            FormatChip(
                label = chip.label,
                description = chip.description,
                onTap = {
                    scope.launch { haptics.light() }
                    onTransform(chip.transform(text))
                }
            )
        }
    }
}

private data class FormatChipConfig(
    val label: String,
    val description: String,
    val transform: (String) -> String
)

private fun getFormatChips(
    clipType: com.clipvault.manager.data.local.entity.ClipType
): List<FormatChipConfig> {
    return when (clipType) {
        com.clipvault.manager.data.local.entity.ClipType.TEXT -> listOf(
            FormatChipConfig("Aa", "Strip HTML formatting") { stripHtml(it) },
            FormatChipConfig("UPPER", "Convert to uppercase") { it.uppercase() },
            FormatChipConfig("Title", "Convert to title case") { toTitleCase(it) },
            FormatChipConfig("⏎", "Normalize whitespace") { it.replace(Regex("\\s+"), " ").trim() }
        )
        com.clipvault.manager.data.local.entity.ClipType.JSON -> listOf(
            FormatChipConfig("Raw", "Keep raw text") { it },
            FormatChipConfig("Pretty", "Pretty print JSON") { prettyPrintJson(it) },
            FormatChipConfig("Strip", "Strip HTML tags") { stripHtml(it) }
        )
        com.clipvault.manager.data.local.entity.ClipType.URL -> listOf(
            FormatChipConfig("Raw", "Keep raw URL") { it }
        )
        else -> listOf(
            FormatChipConfig("Aa", "Strip HTML formatting") { stripHtml(it) },
            FormatChipConfig("⏎", "Normalize whitespace") { it.replace(Regex("\\s+"), " ").trim() }
        )
    }
}

@Composable
private fun FormatChip(label: String, description: String, onTap: () -> Unit) {
    TextButton(
        onClick = onTap,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun normalizeText(text: String): String {
    return text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .trim()
        .trimEnd('\n', '\r')
}

private fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "")
}

private fun toTitleCase(input: String): String {
    return input.split("\\s+".toRegex()).joinToString(" ") { word ->
        if (word.isEmpty()) word
        else word.first().toString().uppercase() + word.substring(1)
    }
}

private fun prettyPrintJson(input: String): String {
    return try {
        val trimmed = input.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val indent = "  "
            var result = ""
            var level = 0
            var inString = false
            var i = 0
            while (i < trimmed.length) {
                val c = trimmed[i]
                when {
                    c == '"' && (i == 0 || trimmed[i - 1] != '\\') -> {
                        inString = !inString
                        result += c
                    }
                    inString -> result += c
                    c == '{' || c == '[' -> {
                        result += c + "\n" + indent.repeat(++level)
                    }
                    c == '}' || c == ']' -> {
                        result += "\n" + indent.repeat(--level) + c
                    }
                    c == ',' -> {
                        result += ",\n" + indent.repeat(level)
                    }
                    c == ':' -> result += ": "
                    else -> result += c
                }
                i++
            }
            result
        } else {
            trimmed
        }
    } catch (_: Exception) {
        input
    }
}