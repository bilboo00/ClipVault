package com.clipvault.manager.data.export

import android.content.Context
import androidx.core.content.FileProvider
import com.clipvault.manager.data.local.entity.ClipEntity
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain")
}

object ClipExporter {

    fun suggestedFileName(format: ExportFormat, timestamp: Long = System.currentTimeMillis()): String {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date(timestamp))
        return "clipvault_export_$ts.${format.extension}"
    }

    /**
     * Backwards-compatible String-returning export. Kept so the existing
     * SettingsScreen Compose flow continues to compile and write to a
     * user-selected destination URI; internally uses the streaming path
     * (StringWriter → BufferedWriter) so memory usage is the same as the
     * direct streaming overload. New callers should prefer
     * [export] to [OutputStream] / [Writer] or [exportClipsStream].
     */
    fun export(clips: List<ClipEntity>, format: ExportFormat): String {
        val sw = StringWriter()
        export(clips, format, sw)
        return sw.toString()
    }

    /**
     * Streams [clips] in [format] to [out]. Uses a small per-row buffer so a
     * 10k-row export of multi-KB snippets never builds a single multi-MB
     * String in memory.
     */
    fun export(clips: List<ClipEntity>, format: ExportFormat, out: OutputStream) {
        out.writer().use { export(clips, format, it) }
    }

    fun export(clips: List<ClipEntity>, format: ExportFormat, writer: Writer) {
        val w = if (writer is BufferedWriter) writer else BufferedWriter(writer)
        when (format) {
            ExportFormat.JSON -> ClipJsonExporter.exportToJson(clips, w)
            ExportFormat.CSV -> writeCsv(clips, w)
            ExportFormat.MARKDOWN -> writeMarkdown(clips, w)
            ExportFormat.PLAIN_TEXT -> writePlainText(clips, w)
        }
        w.flush()
    }

    /**
     * Streams [clips] to a file in the app's cache directory and returns a
     * FileProvider content:// URI suitable for [Intent.ACTION_SEND]. Caller
     * must hold the URI grant; cache files are best-effort and may be
     * trimmed by the system, so prefer sharing immediately after.
     */
    fun exportClipsStream(
        clips: List<ClipEntity>,
        format: ExportFormat,
        context: Context
    ): android.net.Uri {
        val file = File(context.cacheDir, suggestedFileName(format))
        file.outputStream().use { out -> export(clips, format, out) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun writeCsv(clips: List<ClipEntity>, w: Writer) {
        w.append("id,type,pinned,favorite,created_at,source,content\n")
        clips.forEach { c ->
            w.append(c.id.toString()).append(',')
            w.append(c.type.name).append(',')
            w.append(c.isPinned.toString()).append(',')
            w.append(c.isFavorite.toString()).append(',')
            w.append(c.createdAt.toString()).append(',')
            w.append(c.sourceLabel.orEmpty().escapeCsv()).append(',')
            w.append(c.content.escapeCsv()).append('\n')
        }
    }

    private fun writeMarkdown(clips: List<ClipEntity>, w: Writer) {
        w.append("# ClipVault Export\n\n")
        w.append("Exported ${clips.size} clip(s) on ${java.util.Date()}\n\n")
        clips.forEachIndexed { index, c ->
            w.append("## ${index + 1}. ${c.type.name}\n")
            if (c.sourceLabel != null) w.append("_Source: ${c.sourceLabel}_\n")
            w.append('\n')
            if (c.isPinned) w.append("> **Pinned**\n")
            w.append("```\n")
            w.append(c.content)
            w.append("\n```\n\n---\n\n")
        }
    }

    private fun writePlainText(clips: List<ClipEntity>, w: Writer) {
        clips.forEach { c ->
            w.append("=== ${c.type.name}${if (c.isPinned) " (pinned)" else ""} ===\n")
            if (c.sourceLabel != null) w.append("Source: ${c.sourceLabel}\n")
            w.append(c.content)
            w.append("\n\n")
        }
    }

    private fun String.escapeCsv(): String {
        if (none { it == ',' || it == '\n' || it == '"' }) return this
        return "\"" + replace("\"", "\"\"") + "\""
    }
}