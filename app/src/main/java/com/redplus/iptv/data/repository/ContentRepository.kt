package com.redplus.iptv.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.redplus.iptv.data.local.FileCache
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.CategoryItem
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import com.redplus.iptv.data.model.EpgProgram
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.data.model.toCategoryItem
import com.redplus.iptv.data.model.toContentItem
import com.redplus.iptv.data.remote.EpgResponse
import com.redplus.iptv.data.remote.EpisodeDto
import com.redplus.iptv.data.remote.SeriesInfoResponse
import com.redplus.iptv.data.remote.VodInfoResponse
import com.redplus.iptv.data.remote.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ContentRepository(private val client: XtreamClient, private val fileCache: FileCache, private val gson: Gson) {
    suspend fun liveCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "live_categories", force) { client.liveCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun liveStreams(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "live_streams_${categoryId ?: "all"}", force) { client.liveStreams(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun eventStreams(session: Session, force: Boolean = false): List<ContentItem> {
        val categories = liveCategories(session, force)
        val eventCategoryIds = categories.filter { isEventText(it.name) }.map { it.id }.toSet()
        return liveStreams(session, null, force).filter { it.categoryId in eventCategoryIds || isEventText(it.title) }.map { it.copy(type = ContentType.EVENT) }
    }
    suspend fun vodCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "vod_categories", force) { client.vodCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun vodStreams(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "vod_streams_${categoryId ?: "all"}", force) { client.vodStreams(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun seriesCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "series_categories", force) { client.seriesCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun series(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "series_${categoryId ?: "all"}", force) { client.series(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun vodInfo(session: Session, vodId: String): VodInfoResponse = cached(session, "vod_info_$vodId", false) { client.vodInfo(session.serverUrl, session.username, session.password, vodId) }
    suspend fun seriesInfo(session: Session, seriesId: String): SeriesInfoResponse = cached(session, "series_info_$seriesId", false) { client.seriesInfo(session.serverUrl, session.username, session.password, seriesId) }
    suspend fun epg(session: Session, streamId: String, limit: Int = 4): EpgResponse = cached(session, "epg_${streamId}_$limit", false) { client.shortEpg(session.serverUrl, session.username, session.password, streamId, limit) }

    fun buildStreamUrl(session: Session, item: ContentItem): String = buildStreamUrls(session, item, AppSettings()).first()

    fun buildStreamUrls(session: Session, item: ContentItem, settings: AppSettings = AppSettings()): List<String> = when (item.type) {
        ContentType.LIVE, ContentType.EVENT -> client.buildLiveUrls(session.serverUrl, session.username, session.password, item.id, item.directSource, settings.useExtensionInStreamUrl, settings.useM3uFormatInStreamUrl)
        ContentType.MOVIE -> client.buildMovieUrls(session.serverUrl, session.username, session.password, item.id, item.streamExtension, settings.useExtensionInStreamUrl)
        ContentType.SERIES, ContentType.EPISODE -> client.buildSeriesUrls(session.serverUrl, session.username, session.password, item.id, item.streamExtension, settings.useExtensionInStreamUrl)
    }.distinct()

    suspend fun resolvedStreamUrls(session: Session, item: ContentItem, settings: AppSettings): List<String> {
        val urls = buildStreamUrls(session, item, settings)
        if (!settings.streamUrlRedirectionCheck) return urls
        return withContext(Dispatchers.IO) { urls.map { client.resolveRedirect(it) }.distinct() }
    }

    suspend fun timeline(session: Session, item: ContentItem, settings: AppSettings, limit: Int = 8): List<EpgProgram> {
        if (!settings.liveTvEpg || (item.type != ContentType.LIVE && item.type != ContentType.EVENT)) return emptyList()
        val external = if (settings.externalXmlTvUrl.isNotBlank()) runCatching { xmlTvTimeline(settings.externalXmlTvUrl, item, limit) }.getOrDefault(emptyList()) else emptyList()
        if (external.isNotEmpty()) return external
        return epg(session, item.id, limit).listings.orEmpty().map {
            EpgProgram(
                title = it.title,
                description = it.description,
                startTimestamp = it.startTimestamp,
                stopTimestamp = it.stopTimestamp,
                source = "Xtream EPG"
            )
        }
    }

    suspend fun episodeItems(seriesInfo: SeriesInfoResponse): List<ContentItem> = withContext(Dispatchers.Default) {
        seriesInfo.episodes.orEmpty().toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE }).flatMap { (season, episodes) -> episodes.map { it.toContentItem(season) } }
    }

    private suspend fun xmlTvTimeline(xmlUrl: String, item: ContentItem, limit: Int): List<EpgProgram> {
        val xml = cachedRaw("xmltv_${xmlUrl.hashCode()}", force = false, maxAgeMs = 30 * 60 * 1000L) { client.downloadText(xmlUrl) }
        val channelIds = findXmlTvChannelIds(xml, item)
        if (channelIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis() / 1000L
        val programRegex = Regex("<programme\\b([^>]*)>(.*?)</programme>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val result = mutableListOf<EpgProgram>()
        for (match in programRegex.findAll(xml)) {
            val attrs = match.groupValues[1]
            val channel = attr(attrs, "channel") ?: continue
            if (channel !in channelIds) continue
            val start = parseXmlTvTime(attr(attrs, "start"))
            val stop = parseXmlTvTime(attr(attrs, "stop"))
            if (stop != null && stop < now - 300) continue
            val body = match.groupValues[2]
            val title = tagText(body, "title").ifBlank { "Program" }
            val desc = tagText(body, "desc")
            result += EpgProgram(title, desc, start, stop, "XMLTV")
            if (result.size >= limit) break
        }
        return result.sortedWith(compareBy<EpgProgram> { it.startTimestamp ?: Long.MAX_VALUE }.thenBy { it.title })
    }

    private fun findXmlTvChannelIds(xml: String, item: ContentItem): Set<String> {
        val wanted = normalizeName(item.title)
        val epgId = item.epgChannelId?.takeIf { it.isNotBlank() }
        val ids = linkedSetOf<String>()
        if (!epgId.isNullOrBlank()) ids += epgId
        val channelRegex = Regex("<channel\\b([^>]*)>(.*?)</channel>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in channelRegex.findAll(xml)) {
            val id = attr(match.groupValues[1], "id") ?: continue
            val names = Regex("<display-name[^>]*>(.*?)</display-name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(match.groupValues[2]).map { xmlText(it.groupValues[1]) }.toList()
            if (id.equals(epgId, true) || names.any { normalizeName(it).let { n -> n == wanted || n.contains(wanted) || wanted.contains(n) } }) ids += id
        }
        return ids
    }

    private fun EpisodeDto.toContentItem(season: String): ContentItem = ContentItem(
        id = id.orEmpty(), type = ContentType.EPISODE, title = "S${season.padStart(2, '0')}E${episodeNum ?: 0} • ${title.orEmpty().ifBlank { "Episode" }}",
        image = info?.image, plot = info?.plot, rating = info?.rating, streamExtension = containerExtension ?: "mp4"
    )
    private fun isEventText(value: String): Boolean = listOf("ppv", "event", "events", "live event", "sports event", "matchday", "fight night", "ufc", "boxing").any { it in value.lowercase() }

    private suspend inline fun <reified T> cached(session: Session, keyPart: String, force: Boolean, crossinline fetch: suspend () -> T): T {
        val key = "${session.accountKey}::$keyPart"
        val maxAgeMs = 15 * 60 * 1000L
        val cached = fileCache.get(key)
        val fresh = cached != null && System.currentTimeMillis() - cached.updatedAt < maxAgeMs
        if (!force && cached != null && fresh) return gson.fromJson(cached.json, object : TypeToken<T>() {}.type)
        return try {
            val remote = fetch()
            fileCache.put(key, gson.toJson(remote))
            remote
        } catch (e: Exception) {
            if (cached != null) gson.fromJson(cached.json, object : TypeToken<T>() {}.type) else throw e
        }
    }

    private suspend fun cachedRaw(key: String, force: Boolean, maxAgeMs: Long, fetch: suspend () -> String): String {
        val cached = fileCache.get(key)
        val fresh = cached != null && System.currentTimeMillis() - cached.updatedAt < maxAgeMs
        if (!force && cached != null && fresh) return cached.json
        return try {
            val remote = fetch()
            fileCache.put(key, remote)
            remote
        } catch (e: Exception) {
            cached?.json ?: throw e
        }
    }

    private fun attr(attrs: String, name: String): String? = Regex("$name=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.getOrNull(1)
    private fun tagText(body: String, tag: String): String = Regex("<$tag[^>]*>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.getOrNull(1)?.let { xmlText(it) }.orEmpty()
    private fun xmlText(value: String): String = value.replace("<![CDATA[", "").replace("]]>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").trim()
    private fun normalizeName(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun parseXmlTvTime(value: String?): Long? = runCatching { if (value.isNullOrBlank()) null else SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(value.take(20))?.time?.div(1000L) }.getOrNull()
}
