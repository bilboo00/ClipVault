package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.UrlPreviewDao
import com.clipvault.manager.data.local.entity.UrlPreviewEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenGraphFetcher @Inject constructor(
    private val dao: UrlPreviewDao
) {
    suspend fun fetch(url: String): UrlPreviewEntity? = withContext(Dispatchers.IO) {
        val cached = dao.get(url)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < 7L * 86_400_000L) {
            return@withContext cached
        }
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) ClipVault/1.1")
                .timeout(8000)
                .get()

            val title = firstNonBlank(
                meta(doc, "property", "og:title"),
                meta(doc, "name", "title"),
                doc.title().takeIf { it.isNotBlank() }
            )
            val description = firstNonBlank(
                meta(doc, "property", "og:description"),
                meta(doc, "name", "description"),
                meta(doc, "name", "twitter:description")
            )
            val imageUrl = firstNonBlank(
                meta(doc, "property", "og:image"),
                meta(doc, "name", "twitter:image")
            )
            val siteName = firstNonBlank(
                meta(doc, "property", "og:site_name"),
                meta(doc, "name", "application-name")
            )

            val entity = UrlPreviewEntity(
                url = url,
                title = title,
                description = description,
                imageUrl = imageUrl,
                siteName = siteName,
                fetchedAt = System.currentTimeMillis()
            )
            dao.insert(entity)
            entity
        } catch (_: Exception) {
            null
        }
    }

    private fun meta(doc: org.jsoup.nodes.Document, attr: String, key: String): String? {
        val el = doc.select("meta[$attr=$key]").firstOrNull() ?: return null
        return el.attr("content").takeIf { it.isNotBlank() }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}