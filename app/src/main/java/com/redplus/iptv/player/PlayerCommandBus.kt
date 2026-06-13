package com.redplus.iptv.player

import com.redplus.iptv.data.model.ContentItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class PlayerPipCommand(val action: String) {
    AudioOnly("com.redplus.iptv.player.PIP_AUDIO_ONLY"),
    PlayPause("com.redplus.iptv.player.PIP_PLAY_PAUSE"),
    FullScreen("com.redplus.iptv.player.PIP_FULL_SCREEN"),
    EnterPip("com.redplus.iptv.player.ENTER_PIP"),
    ExitPip("com.redplus.iptv.player.EXIT_PIP");

    companion object {
        fun fromAction(action: String?): PlayerPipCommand? = entries.firstOrNull { it.action == action }
    }
}

data class ActivePipState(val item: ContentItem, val isVod: Boolean, val isPlaying: Boolean)

object PlayerCommandBus {
    private val mutableCommands = MutableSharedFlow<PlayerPipCommand>(extraBufferCapacity = 16)
    val commands = mutableCommands.asSharedFlow()
    @Volatile var activePipState: ActivePipState? = null

    fun dispatch(action: String?) {
        PlayerPipCommand.fromAction(action)?.let { mutableCommands.tryEmit(it) }
    }

    fun dispatch(command: PlayerPipCommand) {
        mutableCommands.tryEmit(command)
    }
}

object PlayerOpenBus {
    private val mutableOpenRequests = MutableSharedFlow<ContentItem>(extraBufferCapacity = 1)
    val openRequests = mutableOpenRequests.asSharedFlow()
    @Volatile private var pendingOpenItem: ContentItem? = null

    fun open(item: ContentItem) {
        pendingOpenItem = item
        mutableOpenRequests.tryEmit(item)
    }

    fun consumePending(): ContentItem? {
        val item = pendingOpenItem
        pendingOpenItem = null
        return item
    }
}
