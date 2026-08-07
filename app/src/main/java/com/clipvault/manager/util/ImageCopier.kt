package com.clipvault.manager.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies image content URIs into app-internal storage so they survive the
 * source app going away. Shared by the monitor service (clipboard images)
 * and MainActivity (share-sheet images).
 */
object ImageCopier {

    /** Copies [sourceUri] to `filesDir/clip_<timestamp>.png`; returns the path or null. */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val fileName = "clip_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, fileName)
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
