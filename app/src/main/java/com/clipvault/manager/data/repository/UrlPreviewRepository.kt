package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.UrlPreviewDao
import com.clipvault.manager.data.local.entity.UrlPreviewEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background fetches HTML for a URL and extracts the <title> tag.
 * Persists to UrlPreviewEntity so the result is cached across app launches.
 *
 * Best-effort: never throws. Failure → title remains null.
 */
@Singleton
class UrlPreviewRepository @Inject constructor(
    private val dao: UrlPreviewDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val titlePattern = Pattern.compile(
        "<title[^>]*>(.*?)</title>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    /** Returns cached title or null. */
    suspend fun getCached(url: String): String? =
        dao.get(url)?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MS }?.title

    /**
     * Fire-and-forget fetch. Result is written to DB; observers of [dao] get
     * the new value.
     */
    fun fetchInBackground(url: String) {
        scope.launch {
            val title = runCatching { fetchTitle(url) }.getOrNull()
            persist(url, title)
        }
    }

    suspend fun refresh(url: String): String? {
        val title = runCatching { fetchTitle(url) }.getOrNull()
        persist(url, title)
        return title
    }

    /**
     * Persist a fetch result without letting a failure (null title) overwrite
     * a previously cached title. A null result still creates a row when none
     * exists so permanent failures get a negative-cache entry.
     */
    private suspend fun persist(url: String, title: String?) {
        if (title == null && dao.get(url) != null) return
        dao.insert(UrlPreviewEntity(url = url, title = title))
    }

    private fun fetchTitle(url: String): String? {
        // Normalize
        val normalized = if (!url.startsWith("http")) "https://$url" else url

        val conn = (URL(normalized).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", "ClipboardManager/1.0")
            instanceFollowRedirects = true
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val contentType = conn.contentType.orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) return null

            // Read up to 64 KB — enough for <title>
            val stream = conn.inputStream.buffered()
            val bytes = ByteArray(64 * 1024)
            val read = stream.read(bytes)
            stream.close()
            val html = String(bytes, 0, maxOf(read, 0),Charsets.UTF_8)

            val matcher = titlePattern.matcher(html)
            if (!matcher.find()) return null
            return matcher.group(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(150)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7)
    }
}