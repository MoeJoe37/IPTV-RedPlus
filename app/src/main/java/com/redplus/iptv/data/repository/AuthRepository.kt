package com.redplus.iptv.data.repository

import com.redplus.iptv.data.local.SessionStore
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.data.remote.InvalidServerException
import com.redplus.iptv.data.remote.UnsupportedResponseException
import com.redplus.iptv.data.remote.XtreamClient
import kotlinx.coroutines.flow.Flow

class AuthRepository(private val client: XtreamClient, private val sessionStore: SessionStore) {
    val savedSession: Flow<Session?> = sessionStore.session

    suspend fun login(serverUrl: String, username: String, password: String, remember: Boolean): Result<Session> = runCatching {
        if (serverUrl.isBlank()) throw InvalidServerException("Wrong server URL.")
        if (username.isBlank() || password.isBlank()) throw IllegalArgumentException("Username and password are required.")
        val response = client.authenticate(serverUrl, username, password)
        val info = response.userInfo ?: throw UnsupportedResponseException("Unsupported server response.")
        if (info.auth != 1) throw IllegalArgumentException("Invalid username or password.")
        val status = info.status ?: "Active"
        if (status.equals("Expired", true) || status.equals("Banned", true) || status.equals("Disabled", true)) throw IllegalStateException("Account status: $status")
        val session = Session(serverUrl.trim().trimEnd('/'), username.trim(), password, status, info.expDate, info.activeCons, info.maxConnections)
        sessionStore.save(session, remember)
        session
    }

    suspend fun logout() = sessionStore.clear()
}
