package com.clipvault.manager.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.ClipClassifier


/**
 * Renders a quick-action preview strip for certain clip types:
 *  • COLOR_HEX  → swatch with hex label
 *  • PHONE      → tap-to-call chip
 *  • EMAIL     → tap-to-mail chip
 *  • URL        → tap-to-open-in-browser chip
 */
@Composable
fun InlinePreview(
    type: ClipType,
    content: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    when (type) {
        ClipType.COLOR_HEX -> ColorSwatch(content, modifier)
        ClipType.PHONE -> ActionChip(
            text = content,
            label = "Call",
            icon = Icons.Outlined.Call,
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$content"))) } },
            modifier = modifier
        )
        ClipType.EMAIL -> ActionChip(
            text = content,
            label = "Email",
            icon = Icons.Outlined.Email,
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$content"))) } },
            modifier = modifier
        )
        ClipType.URL -> ActionChip(
            text = content,
            label = "Open",
            icon = Icons.Outlined.OpenInBrowser,
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content))) } },
            modifier = modifier
        )
        else -> { /* no preview for other types */ }
    }
}

@Composable
private fun ColorSwatch(hex: String, modifier: Modifier = Modifier) {
    var color by remember(hex) { mutableStateOf<Color?>(null) }
    LaunchedEffect(hex) {
        color = ClipClassifier.parseHexColor(hex)?.let { Color(it) }
    }
    val swatch = color ?: Color.Transparent

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = hex.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
    }
}

/**
 * Loader that fetches a URL preview title asynchronously from the DAO.
 * Returns a remembered state that updates when the cache changes.
 */
@Composable
fun rememberUrlTitle(url: String): String? {
    // For brevity, the home screen uses a simpler flow that calls
    // UrlPreviewRepository directly. This stub stays for future use.
    return null
}