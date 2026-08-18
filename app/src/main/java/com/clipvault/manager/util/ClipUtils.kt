package com.clipvault.manager.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared clipboard-copy / share helpers.
 *
 * Image clips carry an internal file path ([imagePath]); copying or sharing
 * them must produce the actual image via a content:// URI with a read grant —
 * otherwise other apps would see a `file://` path or the literal "[Image]"
 * text. Text clips fall back to plain text.
 */
object ClipUtils {

    private fun imageUriFor(context: Context, imagePath: String): android.net.Uri? =
        if (File(imagePath).exists()) {
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(imagePath)
                )
            }.getOrNull()
        } else null

    fun clipDataFor(context: Context, content: String, imagePath: String?): ClipData {
        val imageUri = imagePath?.let { imageUriFor(context, it) }
        return if (imageUri != null) {
            ClipData.newUri(context.contentResolver, "clip", imageUri)
        } else {
            ClipData.newPlainText("clip", content)
        }
    }

    fun copyToClipboard(context: Context, content: String, imagePath: String? = null) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clipDataFor(context, content, imagePath))
    }

    fun shareClip(context: Context, content: String, imagePath: String?) {
        val imageUri = imagePath?.let { imageUriFor(context, it) }
        val intent = if (imageUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
