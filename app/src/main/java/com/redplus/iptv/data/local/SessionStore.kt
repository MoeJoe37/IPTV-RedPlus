package com.redplus.iptv.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.ContentLoadingStrategy
import androidx.datastore.preferences.preferencesDataStore
import com.redplus.iptv.data.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "redplus_secure_session")

class SessionStore(private val context: Context, private val cryptoManager: CryptoManager = CryptoManager()) {
    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val remember = prefs[Keys.REMEMBER] ?: false
        val encryptedServer = prefs[Keys.SERVER]
        val encryptedUser = prefs[Keys.USERNAME]
        val encryptedPassword = prefs[Keys.PASSWORD]
        if (!remember || encryptedServer.isNullOrBlank() || encryptedUser.isNullOrBlank() || encryptedPassword.isNullOrBlank()) null else runCatching {
            Session(
                serverUrl = cryptoManager.decrypt(encryptedServer), username = cryptoManager.decrypt(encryptedUser), password = cryptoManager.decrypt(encryptedPassword),
                status = prefs[Keys.STATUS] ?: "Unknown", expDate = prefs[Keys.EXPIRY], activeConnections = prefs[Keys.ACTIVE_CONS], maxConnections = prefs[Keys.MAX_CONS]
            )
        }.getOrNull()
    }


    val appSettings: Flow<AppSettings> = context.sessionDataStore.data.map { prefs ->
        AppSettings(
            subtitlesEnabled = prefs[Keys.SUBTITLES_ENABLED] ?: true,
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANGUAGE] ?: "auto",
            externalSubtitleUrl = prefs[Keys.EXTERNAL_SUBTITLE_URL] ?: "",
            forceLandscapeApp = prefs[Keys.FORCE_LANDSCAPE_APP] ?: false,
            streamUrlRedirectionCheck = prefs[Keys.STREAM_REDIRECTION_CHECK] ?: false,
            liveTvEpg = prefs[Keys.LIVE_TV_EPG] ?: true,
            useExtensionInStreamUrl = prefs[Keys.USE_EXTENSION_STREAM_URL] ?: true,
            useM3uFormatInStreamUrl = prefs[Keys.USE_M3U_STREAM_URL] ?: true,
            useRuntimeMovies = prefs[Keys.USE_RUNTIME_MOVIES] ?: true,
            useRuntimeSeries = prefs[Keys.USE_RUNTIME_SERIES] ?: true,
            useUtf8Decode = prefs[Keys.USE_UTF8_DECODE] ?: true,
            contentLoadingStrategy = runCatching { ContentLoadingStrategy.valueOf(prefs[Keys.CONTENT_LOADING_STRATEGY] ?: ContentLoadingStrategy.RUNTIME.name) }.getOrDefault(ContentLoadingStrategy.RUNTIME),
            useExternalLivePlayer = prefs[Keys.EXTERNAL_LIVE_PLAYER] ?: false,
            useExternalVodPlayer = prefs[Keys.EXTERNAL_VOD_PLAYER] ?: false,
            externalXmlTvUrl = prefs[Keys.EXTERNAL_XMLTV_URL] ?: ""
        )
    }

    suspend fun save(session: Session, remember: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.REMEMBER] = remember
            if (remember) {
                prefs[Keys.SERVER] = cryptoManager.encrypt(session.serverUrl)
                prefs[Keys.USERNAME] = cryptoManager.encrypt(session.username)
                prefs[Keys.PASSWORD] = cryptoManager.encrypt(session.password)
                prefs[Keys.STATUS] = session.status
                putOrRemove(prefs, Keys.EXPIRY, session.expDate)
                putOrRemove(prefs, Keys.ACTIVE_CONS, session.activeConnections)
                putOrRemove(prefs, Keys.MAX_CONS, session.maxConnections)
            } else clearSensitive(prefs)
        }
    }

    suspend fun clear() { context.sessionDataStore.edit { prefs -> clearSensitive(prefs) } }

    suspend fun updateSettings(settings: AppSettings) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.SUBTITLES_ENABLED] = settings.subtitlesEnabled
            prefs[Keys.PREFERRED_SUBTITLE_LANGUAGE] = settings.preferredSubtitleLanguage
            prefs[Keys.EXTERNAL_SUBTITLE_URL] = settings.externalSubtitleUrl
            prefs[Keys.FORCE_LANDSCAPE_APP] = settings.forceLandscapeApp
            prefs[Keys.STREAM_REDIRECTION_CHECK] = settings.streamUrlRedirectionCheck
            prefs[Keys.LIVE_TV_EPG] = settings.liveTvEpg
            prefs[Keys.USE_EXTENSION_STREAM_URL] = settings.useExtensionInStreamUrl
            prefs[Keys.USE_M3U_STREAM_URL] = settings.useM3uFormatInStreamUrl
            prefs[Keys.USE_RUNTIME_MOVIES] = settings.useRuntimeMovies
            prefs[Keys.USE_RUNTIME_SERIES] = settings.useRuntimeSeries
            prefs[Keys.USE_UTF8_DECODE] = settings.useUtf8Decode
            prefs[Keys.CONTENT_LOADING_STRATEGY] = settings.contentLoadingStrategy.name
            prefs[Keys.EXTERNAL_LIVE_PLAYER] = settings.useExternalLivePlayer
            prefs[Keys.EXTERNAL_VOD_PLAYER] = settings.useExternalVodPlayer
            prefs[Keys.EXTERNAL_XMLTV_URL] = settings.externalXmlTvUrl
        }
    }


    private fun clearSensitive(prefs: MutablePreferences) {
        prefs.remove(Keys.SERVER); prefs.remove(Keys.USERNAME); prefs.remove(Keys.PASSWORD); prefs.remove(Keys.STATUS); prefs.remove(Keys.EXPIRY); prefs.remove(Keys.ACTIVE_CONS); prefs.remove(Keys.MAX_CONS); prefs[Keys.REMEMBER] = false
    }
    private fun putOrRemove(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) { if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value }

    private object Keys {
        val SERVER = stringPreferencesKey("server"); val USERNAME = stringPreferencesKey("username"); val PASSWORD = stringPreferencesKey("password")
        val STATUS = stringPreferencesKey("status"); val EXPIRY = stringPreferencesKey("expiry"); val ACTIVE_CONS = stringPreferencesKey("active_connections"); val MAX_CONS = stringPreferencesKey("max_connections")
        val REMEMBER = booleanPreferencesKey("remember")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
        val EXTERNAL_SUBTITLE_URL = stringPreferencesKey("external_subtitle_url")
        val FORCE_LANDSCAPE_APP = booleanPreferencesKey("force_landscape_app")
        val STREAM_REDIRECTION_CHECK = booleanPreferencesKey("stream_redirection_check")
        val LIVE_TV_EPG = booleanPreferencesKey("live_tv_epg")
        val USE_EXTENSION_STREAM_URL = booleanPreferencesKey("use_extension_stream_url")
        val USE_M3U_STREAM_URL = booleanPreferencesKey("use_m3u_stream_url")
        val USE_RUNTIME_MOVIES = booleanPreferencesKey("use_runtime_movies")
        val USE_RUNTIME_SERIES = booleanPreferencesKey("use_runtime_series")
        val USE_UTF8_DECODE = booleanPreferencesKey("use_utf8_decode")
        val CONTENT_LOADING_STRATEGY = stringPreferencesKey("content_loading_strategy")
        val EXTERNAL_LIVE_PLAYER = booleanPreferencesKey("external_live_player")
        val EXTERNAL_VOD_PLAYER = booleanPreferencesKey("external_vod_player")
        val EXTERNAL_XMLTV_URL = stringPreferencesKey("external_xmltv_url")
    }
}
