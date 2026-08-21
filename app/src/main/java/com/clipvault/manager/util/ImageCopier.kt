package com.clipvault.manager.util

import android.content.Context
import android.graphics.Bitmap
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

    /** Reused across [decodeBitmapSampled] invocations to skip per-call allocation. */
    private val reusableBoundsOptions = ThreadLocal.withInitial {
        BitmapFactory.Options().apply { inJustDecodeBounds = true }
    }

    /** Reused for the real decode pass when no caller-supplied [BitmapFactory.Options] is used. */
    private val reusableDecodeOptions = ThreadLocal.withInitial { BitmapFactory.Options() }

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
            val bounds = reusableBoundsOptions.get()!!
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                file.delete()
                return null
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes [path] into a Bitmap sized down to fit [reqWidth] x [reqHeight]
     * using a single bounds pass + inSampleSize. Returns null on any decode
     * failure so callers can fall back to a placeholder.
     *
     * Pass a pre-configured [options] to opt into bitmap reuse (e.g. set
     * `inBitmap`); the helper will populate `inSampleSize` and decode through
     * it. With no [options], the thread-local decode options are reused to
     * avoid per-call allocation.
     */
    fun decodeBitmapSampled(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        options: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            val bounds = reusableBoundsOptions.get()!!
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val halfW = bounds.outWidth / 2
            val halfH = bounds.outHeight / 2
            while (halfW / sample >= reqWidth && halfH / sample >= reqHeight) {
                sample *= 2
            }
            if (options != null) {
                options.inSampleSize = sample
                BitmapFactory.decodeFile(path, options)
            } else {
                val opts = reusableDecodeOptions.get()!!
                opts.inSampleSize = sample
                opts.inPreferredConfig = Bitmap.Config.RGB_565
                opts.inBitmap = null
                BitmapFactory.decodeFile(path, opts)
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}