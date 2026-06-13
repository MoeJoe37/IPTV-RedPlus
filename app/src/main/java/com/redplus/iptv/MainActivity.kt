package com.redplus.iptv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.redplus.iptv.player.AudioOnlyPlaybackService
import com.redplus.iptv.player.PlayerCommandBus
import com.redplus.iptv.player.PlayerOpenBus
import com.redplus.iptv.player.PlayerPipCommand
import com.redplus.iptv.player.enterRedPlusPip
import com.redplus.iptv.ui.RedPlusApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestNotificationPermissionIfNeeded()
        handlePlayerIntent(intent)
        val container = (application as RedPlusApplication).container
        setContent { RedPlusApp(container = container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = PlayerCommandBus.activePipState ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            PlayerCommandBus.dispatch(PlayerPipCommand.EnterPip)
            enterRedPlusPip(item = state.item, isVod = state.isVod, isPlaying = state.isPlaying)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerCommandBus.dispatch(if (isInPictureInPictureMode) PlayerPipCommand.EnterPip else PlayerPipCommand.ExitPip)
    }

    private fun handlePlayerIntent(intent: Intent?) {
        if (intent?.action == AudioOnlyPlaybackService.ACTION_OPEN_STREAM) {
            AudioOnlyPlaybackService.itemFromIntent(intent)?.let(PlayerOpenBus::open)
            return
        }
        PlayerCommandBus.dispatch(intent?.action)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4301)
        }
    }
}
