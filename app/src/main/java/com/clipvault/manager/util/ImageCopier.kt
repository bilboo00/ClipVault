package com.clipvault.manager.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Copies image content URIs into app-internal storage so they survive the
 * source app going away. Shared by the monitor service (clipboard images)
 * and MainActivity (share-sheet images).
 */
object ImageCopier {

    /** Cap on a single copied image; larger payloads are rejected. */
    const val MAX_IMAGE_BYTES = 50 * 1024 * 1024L

    private val counter = AtomicLong(0)

    /**
     * Copies [sourceUri] to `filesDir/images/clip_<timestamp>_<seq>.png`;
     * returns the path or null. Rejects oversized and undecodable content.
     */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists() && !imagesDir.mkdirs()) return null
            val fileName = "clip_${System.currentTimeMillis()}_${counter.incrementAndGet()}.png"
            val file = File(imagesDir, fileName)
            inputStream.use { input ->
                file.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) return null
                        output.write(buffer, 0, read)
                    }
                }
            }
            // Reject payloads that don't decode as a bitmap (e.g. a truncated
            // or non-image share even when the MIME type says otherwise).
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                file.delete()
                return null
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}