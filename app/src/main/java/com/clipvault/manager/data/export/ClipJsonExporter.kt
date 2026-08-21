package com.clipvault.manager.data.export

import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.Writer

object ClipJsonExporter {

    /**
     * Streams [clips] to [writer] as a JSON object of shape
     * `{ version, exportedAt, clips: [...] }`. Uses a small per-row buffer
     * rather than building the full document as a String, so a 10k-row
     * export stays well under memory caps.
     */
    fun exportToJson(clips: List<ClipEntity>, writer: Writer) {
        val w = if (writer is BufferedWriter) writer else BufferedWriter(writer)
        w.append("{\"version\":1,")
        w.append("\"exportedAt\":").append(System.currentTimeMillis().toString()).append(',')
        w.append("\"clips\":[")
        clips.forEachIndexed { index, clip ->
            if (index > 0) w.append(',')
            w.append("{\"content\":")
            appendJsonString(w, clip.content)
            w.append(",\"type\":")
            appendJsonString(w, clip.type.name)
            w.append(",\"isPinned\":").append(clip.isPinned.toString())
            w.append(",\"isFavorite\":").append(clip.isFavorite.toString())
            w.append(",\"createdAt\":").append(clip.createdAt.toString())
            w.append(",\"sourceLabel\":")
            if (clip.sourceLabel == null) w.append("null") else appendJsonString(w, clip.sourceLabel)
            w.append(",\"sortOrder\":").append(clip.sortOrder.toString())
            w.append('}')
        }
        w.append("]}")
        w.flush()
    }

    fun importFromJson(json: String): List<ClipEntity> {
        val wrapper = JSONObject(json)
        val array = wrapper.getJSONArray("clips")
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ClipEntity(
                content = obj.getString("content"),
                type = runCatching { ClipType.valueOf(obj.getString("type")) }.getOrDefault(ClipType.TEXT),
                isPinned = obj.optBoolean("isPinned", false),
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                sourceLabel = if (obj.isNull("sourceLabel")) null else obj.optString("sourceLabel").takeIf { it != "null" },
                sortOrder = obj.optInt("sortOrder", 0)
            )
        }
    }

    private fun appendJsonString(w: Writer, raw: String) {
        w.append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> w.append("\\\"")
                '\\' -> w.append("\\\\")
                '\n' -> w.append("\\n")
                '\r' -> w.append("\\r")
                '\t' -> w.append("\\t")
                '\b' -> w.append("\\b")
                '\u000C' -> w.append("\\f")
                else -> if (ch.code < 0x20) {
                    w.append("\\u").append(String.format("%04x", ch.code))
                } else {
                    w.append(ch)
                }
            }
        }
        w.append('"')
    }
}