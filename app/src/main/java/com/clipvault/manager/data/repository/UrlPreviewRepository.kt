package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.UrlPreviewDao
import com.clipvault.manager.data.local.entity.UrlPreviewEntity
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpRetryException
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
    private val titlePattern = Pattern.compile(
        "<title[^>]*>(.*?)</title>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    /** Returns cached title or null. */
    suspend fun getCached(url: String): String? =
        dao.get(url)?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MS }?.title

    suspend fun refresh(url: String): String? {
        val title = runCatching { fetchOnce(url) }.getOrNull()
        persist(url, title)
        return title
    }

    /**
     * Persist a fetch result. Negative cache writes (null title) overwrite any
     * existing row via [UrlPreviewDao.insert]'s REPLACE conflict strategy, so
     * two concurrent failed fetches can't both pass a stale existence check
     * and double-write; a transient failure now just overwrites the same
     * earlier transient-failure row.
     */
    private suspend fun persist(url: String, title: String?) {
        dao.insert(UrlPreviewEntity(url = url, title = title))
    }

    private suspend fun fetchOnce(url: String, attempt: Int = 0): String? {
        // Normalize
        val normalized = if (!url.startsWith("http")) "https://$url" else url

        val conn = (URL(normalized).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", "ClipboardManager/1.0")
            instanceFollowRedirects = true
            // Avoid stale platform HttpURLConnection cache hits that can vary
            // across devices and Android versions.
            useCaches = false
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val contentType = conn.contentType.orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) return null

            // Read up to 64 KB — enough for <title>. InputStream.read(byte[])
            // is contractually allowed to return fewer bytes than requested,
            // so loop until -1 (or buffer full) or the title tag past byte
            // 65535 would never be parsed.
            val stream = conn.inputStream.buffered()
            val bytes = ByteArray(64 * 1024)
            var totalRead = 0
            while (totalRead < bytes.size) {
                val read = stream.read(bytes, totalRead, bytes.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            stream.close()
            val html = String(bytes, 0, totalRead, Charsets.UTF_8)

            val matcher = titlePattern.matcher(html)
            if (!matcher.find()) return null
            return matcher.group(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(150)
        } catch (e: HttpRetryException) {
            // Server explicitly asked us to retry — don't loop on our own backoff.
            return null
        } catch (e: IOException) {
            // Transient network failures (SocketTimeoutException,
            // UnknownHostException, ConnectException, …) get up to two retries
            // with exponential backoff before giving up.
            if (attempt < 2) {
                delay(200L * (1L shl attempt))
                return fetchOnce(url, attempt + 1)
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7)
    }
}