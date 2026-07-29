package com.clipvault.manager.data.export

import com.clipvault.manager.data.local.entity.ClipEntity

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain")
}

object ClipExporter {

    fun export(clips: List<ClipEntity>, format: ExportFormat): String = when (format) {
        ExportFormat.JSON -> exportJson(clips)
        ExportFormat.CSV -> exportCsv(clips)
        ExportFormat.MARKDOWN -> exportMarkdown(clips)
        ExportFormat.PLAIN_TEXT -> exportPlainText(clips)
    }

    fun suggestedFileName(format: ExportFormat, timestamp: Long = System.currentTimeMillis()): String {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date(timestamp))
        return "clipvault_export_$ts.${format.extension}"
    }

    private fun exportJson(clips: List<ClipEntity>): String =
        ClipJsonExporter.exportToJson(clips)

    private fun exportCsv(clips: List<ClipEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("id,type,pinned,favorite,created_at,source,content")
        clips.forEach { c ->
            sb.appendLine(
                listOf(
                    c.id.toString(),
                    c.type.name,
                    c.isPinned.toString(),
                    c.isFavorite.toString(),
                    c.createdAt.toString(),
                    c.sourceLabel.orEmpty().escapeCsv(),
                    c.content.escapeCsv()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun exportMarkdown(clips: List<ClipEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("# ClipVault Export")
        sb.appendLine()
        sb.appendLine("Exported ${clips.size} clip(s) on ${java.util.Date()}")
        sb.appendLine()
        clips.forEachIndexed { index, c ->
            sb.appendLine("## ${index + 1}. ${c.type.name}")
            if (c.sourceLabel != null) sb.appendLine("_Source: ${c.sourceLabel}_")
            sb.appendLine()
            if (c.isPinned) sb.appendLine("> **Pinned**")
            sb.appendLine("```")
            sb.appendLine(c.content)
            sb.appendLine("```")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun exportPlainText(clips: List<ClipEntity>): String {
        val sb = StringBuilder()
        clips.forEach { c ->
            sb.appendLine("=== ${c.type.name}${if (c.isPinned) " (pinned)" else ""} ===")
            if (c.sourceLabel != null) sb.appendLine("Source: ${c.sourceLabel}")
            sb.appendLine(c.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun String.escapeCsv(): String {
        if (none { it == ',' || it == '\n' || it == '"' }) return this
        return "\"" + replace("\"", "\"\"") + "\""
    }
}