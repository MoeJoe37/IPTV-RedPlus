package com.redplus.iptv.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class PlayerPipCommand(val action: String) {
    AudioOnly("com.redplus.iptv.player.PIP_AUDIO_ONLY"),
    PlayPause("com.redplus.iptv.player.PIP_PLAY_PAUSE"),
    FullScreen("com.redplus.iptv.player.PIP_FULL_SCREEN");

    companion object {
        fun fromAction(action: String?): PlayerPipCommand? = entries.firstOrNull { it.action == action }
    }
}

object PlayerCommandBus {
    private val mutableCommands = MutableSharedFlow<PlayerPipCommand>(extraBufferCapacity = 8)
    val commands = mutableCommands.asSharedFlow()

    fun dispatch(action: String?) {
        PlayerPipCommand.fromAction(action)?.let { mutableCommands.tryEmit(it) }
    }
}
