package com.redplus.iptv

import android.app.Application
import com.redplus.iptv.data.AppContainer

class RedPlusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
