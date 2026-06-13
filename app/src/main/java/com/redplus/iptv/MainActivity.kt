package com.redplus.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.redplus.iptv.player.PlayerCommandBus
import com.redplus.iptv.ui.RedPlusApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        handlePipAction(intent)
        val container = (application as RedPlusApplication).container
        setContent { RedPlusApp(container = container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePipAction(intent)
    }

    private fun handlePipAction(intent: Intent?) {
        PlayerCommandBus.dispatch(intent?.action)
    }
}
