package com.redplus.iptv.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerPipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PlayerPipCommand.fromAction(intent?.action)?.let(PlayerCommandBus::dispatch)
    }
}
