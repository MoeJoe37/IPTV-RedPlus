package com.redplus.iptv.data.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.NetworkRouteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidServerException(message: String) : Exception(message)
class ServerTimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UnsupportedResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object NetworkRouteState {
    @Volatile var settings: AppSettings = AppSettings()
    @Volatile var lastAutoRouteLabel: String = "Direct connection"
    @Volatile var lastAutoLatencyMs: Long = -1L
    @Volatile var lastCheckedAt: Long = 0L
}

private data class PublicDnsProvider(
    val mode: NetworkRouteMode,
    val label: String,
    val dohUrl: String,
    val bootstrapIps: List<String>
)

class XtreamClient(
    private val gson: Gson = Gson(),
    private val baseOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val dnsProviders = listOf(
        PublicDnsProvider(NetworkRouteMode.CLOUDFLARE_1111, "Cloudflare 1.1.1.1", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
        PublicDnsProvider(NetworkRouteMode.GOOGLE_DNS, "Google Public DNS", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
        PublicDnsProvider(NetworkRouteMode.QUAD9_DNS, "Quad9 DNS", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
        PublicDnsProvider(NetworkRouteMode.ADGUARD_DNS, "AdGuard DNS", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15"))
    )

    @Volatile private var cachedClientKey: String = ""
    @Volatile private var cachedClient: OkHttpClient = baseOkHttpClient
    @Volatile private var cachedAutoProvider: PublicDnsProvider? = null

    suspend fun authenticate(serverUrl: String, username: String, password: String): XtreamLoginResponse = get(serverUrl, username, password, null, emptyMap(), XtreamLoginResponse::class.java)
    suspend fun liveCategories(serverUrl: String, username: String, password: String): List<CategoryDto> = getArray(serverUrl, username, password, "get_live_categories", emptyMap(), Array<CategoryDto>::class.java).toList()
    suspend fun liveStreams(serverUrl: String, username: String, password: String, categoryId: String? = null): List<LiveStreamDto> = getArray(serverUrl, username, password, "get_live_streams", optionalCategory(categoryId), Array<LiveStreamDto>::class.java).toList()
    suspend fun vodCategories(serverUrl: String, username: String, password: String): List<CategoryDto> = getArray(serverUrl, username, password, "get_vod_categories", emptyMap(), Array<CategoryDto>::class.java).toList()
    suspend fun vodStreams(serverUrl: String, username: String, password: String, categoryId: String? = null): List<VodStreamDto> = getArray(serverUrl, username, password, "get_vod_streams", optionalCategory(categoryId), Array<VodStreamDto>::class.java).toList()
    suspend fun vodInfo(serverUrl: String, username: String, password: String, vodId: String): VodInfoResponse = get(serverUrl, username, password, "get_vod_info", mapOf("vod_id" to vodId), VodInfoResponse::class.java)
    suspend fun seriesCategories(serverUrl: String, username: String, password: String): List<CategoryDto> = getArray(serverUrl, username, password, "get_series_categories", emptyMap(), Array<CategoryDto>::class.java).toList()
    suspend fun series(serverUrl: String, username: String, password: String, categoryId: String? = null): List<SeriesDto> = getArray(serverUrl, username, password, "get_series", optionalCategory(categoryId), Array<SeriesDto>::class.java).toList()
    suspend fun seriesInfo(serverUrl: String, username: String, password: String, seriesId: String): SeriesInfoResponse = get(serverUrl, username, password, "get_series_info", mapOf("series_id" to seriesId), SeriesInfoResponse::class.java)
    suspend fun shortEpg(serverUrl: String, username: String, password: String, streamId: String, limit: Int = 4): EpgResponse = get(serverUrl, username, password, "get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString()), EpgResponse::class.java)

    fun buildLiveUrl(serverUrl: String, username: String, password: String, streamId: String): String = buildLiveUrls(serverUrl, username, password, streamId, null, true, true).first()
    fun buildMovieUrl(serverUrl: String, username: String, password: String, streamId: String, extension: String?): String = buildMovieUrls(serverUrl, username, password, streamId, extension, true).first()
    fun buildSeriesUrl(serverUrl: String, username: String, password: String, episodeId: String, extension: String?): String = buildSeriesUrls(serverUrl, username, password, episodeId, extension, true).first()

    fun buildLiveUrls(serverUrl: String, username: String, password: String, streamId: String, directSource: String?, useExtension: Boolean, useM3u: Boolean): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        val urls = mutableListOf<String>()
        directSource?.takeIf { it.isNotBlank() }?.let { urls += it }
        if (useExtension) urls += "$base/live/$user/$pass/$streamId.ts"
        if (useM3u) urls += "$base/live/$user/$pass/$streamId.m3u8"
        urls += "$base/live/$user/$pass/$streamId"
        return urls.distinct()
    }

    fun buildMovieUrls(serverUrl: String, username: String, password: String, streamId: String, extension: String?, useExtension: Boolean): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        val ext = extension.cleanExtension("mp4")
        val urls = mutableListOf<String>()
        if (useExtension) {
            urls += "$base/movie/$user/$pass/$streamId.$ext"
            if (ext != "mp4") urls += "$base/movie/$user/$pass/$streamId.mp4"
            if (ext != "m3u8") urls += "$base/movie/$user/$pass/$streamId.m3u8"
            if (ext != "mkv") urls += "$base/movie/$user/$pass/$streamId.mkv"
        }
        urls += "$base/movie/$user/$pass/$streamId"
        return urls.distinct()
    }

    fun buildSeriesUrls(serverUrl: String, username: String, password: String, episodeId: String, extension: String?, useExtension: Boolean): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        val ext = extension.cleanExtension("mp4")
        val urls = mutableListOf<String>()
        if (useExtension) {
            urls += "$base/series/$user/$pass/$episodeId.$ext"
            if (ext != "mp4") urls += "$base/series/$user/$pass/$episodeId.mp4"
            if (ext != "m3u8") urls += "$base/series/$user/$pass/$episodeId.m3u8"
            if (ext != "mkv") urls += "$base/series/$user/$pass/$episodeId.mkv"
        }
        urls += "$base/series/$user/$pass/$episodeId"
        return urls.distinct()
    }

    fun mediaOkHttpClient(): OkHttpClient = routedClient()
    fun currentRouteLabel(): String = when (val mode = NetworkRouteState.settings.networkRouteMode) {
        NetworkRouteMode.AUTO_DNS -> "${NetworkRouteState.lastAutoRouteLabel}${NetworkRouteState.lastAutoLatencyMs.takeIf { it >= 0 }?.let { " • ${it}ms" } ?: ""}"
        NetworkRouteMode.DIRECT -> mode.label
        NetworkRouteMode.MANUAL_HTTP_PROXY, NetworkRouteMode.MANUAL_SOCKS_PROXY -> {
            val s = NetworkRouteState.settings
            "${mode.label}: ${s.manualProxyHost}:${s.manualProxyPort}"
        }
        else -> mode.label
    }

    suspend fun resolveRedirect(url: String): String = withContext(Dispatchers.IO) {
        val client = routedClient()
        runCatching {
            val request = Request.Builder().url(url).head().header("User-Agent", "RedPlusIPTV/1.3").build()
            client.newCall(request).execute().use { response -> response.request.url.toString() }
        }.getOrElse {
            runCatching {
                val request = Request.Builder().url(url).get().header("Range", "bytes=0-1").header("User-Agent", "RedPlusIPTV/1.3").build()
                client.newCall(request).execute().use { response -> response.request.url.toString() }
            }.getOrDefault(url)
        }
    }

    suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) { execute(url) }

    private fun optionalCategory(categoryId: String?): Map<String, String> = if (categoryId.isNullOrBlank() || categoryId == "all") emptyMap() else mapOf("category_id" to categoryId)

    private suspend fun <T> get(serverUrl: String, username: String, password: String, action: String?, extra: Map<String, String>, clazz: Class<T>): T = withContext(Dispatchers.IO) {
        val body = execute(buildApiUrl(serverUrl, username, password, action, extra))
        try { gson.fromJson(body, clazz) ?: throw UnsupportedResponseException("The server returned an empty response.") }
        catch (e: JsonSyntaxException) { throw UnsupportedResponseException("The server response is not a supported Xtream JSON format.", e) }
    }

    private suspend fun <T> getArray(serverUrl: String, username: String, password: String, action: String, extra: Map<String, String>, clazz: Class<T>): T = withContext(Dispatchers.IO) {
        val body = execute(buildApiUrl(serverUrl, username, password, action, extra))
        try { gson.fromJson(body, clazz) ?: throw UnsupportedResponseException("The server returned an empty list.") }
        catch (e: JsonSyntaxException) { throw UnsupportedResponseException("The server response is not a supported Xtream list format.", e) }
    }

    private fun execute(url: String): String {
        val request = Request.Builder().url(url).get().header("User-Agent", "RedPlusIPTV/1.3").build()
        try {
            routedClient().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw XtreamException("Server returned HTTP ${response.code}.")
                if (body.isBlank()) throw UnsupportedResponseException("The server returned an empty response.")
                return body
            }
        } catch (e: SocketTimeoutException) { throw ServerTimeoutException("The server timed out.", e)
        } catch (e: IOException) { throw XtreamException("Could not connect to the server. Check internet connection, server URL, and Network route settings.", e) }
    }

    private fun routedClient(): OkHttpClient {
        val settings = NetworkRouteState.settings
        val key = listOf(settings.networkRouteMode.name, settings.autoNetworkSwitch, settings.manualProxyHost, settings.manualProxyPort, autoProviderKey(settings)).joinToString("|")
        if (key == cachedClientKey) return cachedClient

        val next = when (settings.networkRouteMode) {
            NetworkRouteMode.DIRECT -> baseOkHttpClient
            NetworkRouteMode.AUTO_DNS -> providerClient(selectAutoDnsProvider(settings))
            NetworkRouteMode.CLOUDFLARE_1111, NetworkRouteMode.GOOGLE_DNS, NetworkRouteMode.QUAD9_DNS, NetworkRouteMode.ADGUARD_DNS ->
                providerClient(dnsProviders.first { it.mode == settings.networkRouteMode })
            NetworkRouteMode.MANUAL_HTTP_PROXY -> proxyClient(Proxy.Type.HTTP, settings)
            NetworkRouteMode.MANUAL_SOCKS_PROXY -> proxyClient(Proxy.Type.SOCKS, settings)
        }
        cachedClientKey = key
        cachedClient = next
        return next
    }

    private fun autoProviderKey(settings: AppSettings): String {
        if (settings.networkRouteMode != NetworkRouteMode.AUTO_DNS) return ""
        val now = System.currentTimeMillis()
        val interval = if (settings.autoNetworkSwitch) 2 * 60_000L else 15 * 60_000L
        if (cachedAutoProvider == null || now - NetworkRouteState.lastCheckedAt > interval) selectAutoDnsProvider(settings)
        return cachedAutoProvider?.label.orEmpty()
    }

    private fun selectAutoDnsProvider(settings: AppSettings): PublicDnsProvider {
        val now = System.currentTimeMillis()
        val interval = if (settings.autoNetworkSwitch) 2 * 60_000L else 15 * 60_000L
        cachedAutoProvider?.let { if (now - NetworkRouteState.lastCheckedAt <= interval) return it }

        var best: PublicDnsProvider? = null
        var bestMs = Long.MAX_VALUE
        for (provider in dnsProviders) {
            val elapsed = measureDnsProvider(provider)
            if (elapsed in 0 until bestMs) {
                best = provider
                bestMs = elapsed
            }
        }
        val selected = best ?: dnsProviders.first()
        cachedAutoProvider = selected
        NetworkRouteState.lastAutoRouteLabel = selected.label
        NetworkRouteState.lastAutoLatencyMs = if (bestMs == Long.MAX_VALUE) -1 else bestMs
        NetworkRouteState.lastCheckedAt = now
        return selected
    }

    private fun measureDnsProvider(provider: PublicDnsProvider): Long = runCatching {
        val dns = dnsOverHttps(provider)
        measureTimeMillis { dns.lookup("example.com") }
    }.getOrDefault(Long.MAX_VALUE)

    private fun providerClient(provider: PublicDnsProvider): OkHttpClient =
        baseOkHttpClient.newBuilder().dns(dnsOverHttps(provider)).build()

    private fun dnsOverHttps(provider: PublicDnsProvider): DnsOverHttps {
        val hosts = provider.bootstrapIps.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }.toTypedArray()
        return DnsOverHttps.Builder()
            .client(baseOkHttpClient)
            .url(provider.dohUrl.toHttpUrl())
            .bootstrapDnsHosts(*hosts)
            .build()
    }

    private fun proxyClient(type: Proxy.Type, settings: AppSettings): OkHttpClient {
        val host = settings.manualProxyHost.trim()
        val port = settings.manualProxyPort.coerceIn(0, 65535)
        if (host.isBlank() || port == 0) return baseOkHttpClient
        return baseOkHttpClient.newBuilder()
            .proxy(Proxy(type, InetSocketAddress(host, port)))
            .build()
    }

    private fun buildApiUrl(serverUrl: String, username: String, password: String, action: String?, extra: Map<String, String>): String {
        val normalized = normalizeServer(serverUrl)
        val base = "$normalized/player_api.php".toHttpUrlOrNull() ?: throw InvalidServerException("Wrong server URL. Use a valid Xtream server base URL.")
        val builder = base.newBuilder().addQueryParameter("username", username).addQueryParameter("password", password)
        if (!action.isNullOrBlank()) builder.addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun normalizeServer(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        if (withScheme.toHttpUrlOrNull() == null) throw InvalidServerException("Wrong server URL. Use http:// or https:// with a valid host.")
        return withScheme
    }

    private fun String.urlPart(): String = trim().replace("/", "%2F")
    private fun String?.cleanExtension(default: String): String = this?.trim()?.trim('.')?.ifBlank { default } ?: default
}
