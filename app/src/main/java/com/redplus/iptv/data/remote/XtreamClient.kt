package com.redplus.iptv.data.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidServerException(message: String) : Exception(message)
class ServerTimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UnsupportedResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class XtreamClient(
    private val gson: Gson = Gson(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
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

    fun buildLiveUrl(serverUrl: String, username: String, password: String, streamId: String): String = buildLiveUrls(serverUrl, username, password, streamId, null).first()
    fun buildMovieUrl(serverUrl: String, username: String, password: String, streamId: String, extension: String?): String = buildMovieUrls(serverUrl, username, password, streamId, extension).first()
    fun buildSeriesUrl(serverUrl: String, username: String, password: String, episodeId: String, extension: String?): String = buildSeriesUrls(serverUrl, username, password, episodeId, extension).first()

    fun buildLiveUrls(serverUrl: String, username: String, password: String, streamId: String, directSource: String?): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        return listOfNotNull(
            directSource?.takeIf { it.isNotBlank() },
            "$base/live/$user/$pass/$streamId.ts",
            "$base/live/$user/$pass/$streamId.m3u8",
            "$base/live/$user/$pass/$streamId"
        )
    }

    fun buildMovieUrls(serverUrl: String, username: String, password: String, streamId: String, extension: String?): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        val ext = extension.cleanExtension("mp4")
        return listOf("$base/movie/$user/$pass/$streamId.$ext", "$base/movie/$user/$pass/$streamId")
    }

    fun buildSeriesUrls(serverUrl: String, username: String, password: String, episodeId: String, extension: String?): List<String> {
        val base = normalizeServer(serverUrl)
        val user = username.urlPart()
        val pass = password.urlPart()
        val ext = extension.cleanExtension("mp4")
        return listOf("$base/series/$user/$pass/$episodeId.$ext", "$base/series/$user/$pass/$episodeId")
    }

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
        val request = Request.Builder().url(url).get().header("User-Agent", "RedPlusIPTV/1.0").build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw XtreamException("Server returned HTTP ${response.code}.")
                if (body.isBlank()) throw UnsupportedResponseException("The server returned an empty response.")
                return body
            }
        } catch (e: SocketTimeoutException) { throw ServerTimeoutException("The server timed out.", e)
        } catch (e: IOException) { throw XtreamException("Could not connect to the server. Check internet connection and server URL.", e) }
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
