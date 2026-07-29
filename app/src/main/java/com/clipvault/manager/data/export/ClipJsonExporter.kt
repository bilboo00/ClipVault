package com.clipvault.manager.data.export

import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import org.json.JSONArray
import org.json.JSONObject

object ClipJsonExporter {

    fun exportToJson(clips: List<ClipEntity>): String {
        val array = JSONArray()
        clips.forEach { clip ->
            val obj = JSONObject().apply {
                put("content", clip.content)
                put("type", clip.type.name)
                put("isPinned", clip.isPinned)
                put("isFavorite", clip.isFavorite)
                put("createdAt", clip.createdAt)
                put("sourceLabel", clip.sourceLabel)
                put("sortOrder", clip.sortOrder)
            }
            array.put(obj)
        }
        val wrapper = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("clips", array)
        }
        return wrapper.toString(2)
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
                sourceLabel = obj.optString("sourceLabel", null).takeIf { it != "null" },
                sortOrder = obj.optInt("sortOrder", 0)
            )
        }
    }
}
