package com.redplus.iptv.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private fun clearSensitive(prefs: MutablePreferences) {
        prefs.remove(Keys.SERVER); prefs.remove(Keys.USERNAME); prefs.remove(Keys.PASSWORD); prefs.remove(Keys.STATUS); prefs.remove(Keys.EXPIRY); prefs.remove(Keys.ACTIVE_CONS); prefs.remove(Keys.MAX_CONS); prefs[Keys.REMEMBER] = false
    }
    private fun putOrRemove(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) { if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value }

    private object Keys {
        val SERVER = stringPreferencesKey("server"); val USERNAME = stringPreferencesKey("username"); val PASSWORD = stringPreferencesKey("password")
        val STATUS = stringPreferencesKey("status"); val EXPIRY = stringPreferencesKey("expiry"); val ACTIVE_CONS = stringPreferencesKey("active_connections"); val MAX_CONS = stringPreferencesKey("max_connections")
        val REMEMBER = booleanPreferencesKey("remember")
    }
}
