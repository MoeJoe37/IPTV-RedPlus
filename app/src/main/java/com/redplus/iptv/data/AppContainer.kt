package com.redplus.iptv.data

import android.content.Context
import com.google.gson.Gson
import com.redplus.iptv.data.local.FileCache
import com.redplus.iptv.data.local.RedPlusDatabase
import com.redplus.iptv.data.local.SessionStore
import com.redplus.iptv.data.remote.XtreamClient
import com.redplus.iptv.data.remote.VpnGateClient
import com.redplus.iptv.data.repository.AuthRepository
import com.redplus.iptv.data.repository.ContentRepository
import com.redplus.iptv.data.repository.LibraryRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val database = RedPlusDatabase.get(appContext)
    private val fileCache = FileCache(appContext)
    val sessionStore = SessionStore(appContext)
    val xtreamClient = XtreamClient(gson = gson)
    val vpnGateClient = VpnGateClient()
    val authRepository = AuthRepository(xtreamClient, sessionStore)
    val contentRepository = ContentRepository(xtreamClient, fileCache, gson)
    val libraryRepository = LibraryRepository(database.favoriteDao(), database.watchHistoryDao(), database.recentSearchDao(), database.cacheDao(), fileCache)
}
