package com.redplus.iptv.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redplus.iptv.R
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.model.AppSettings
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import com.redplus.iptv.data.model.EpgProgram
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.ui.ErrorState
import com.redplus.iptv.ui.GlassPanel
import com.redplus.iptv.ui.LoadingState
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.ui.theme.PremiumPanel
import com.redplus.iptv.ui.theme.PremiumRed
import com.redplus.iptv.util.msToTime
import com.redplus.iptv.util.unixToLocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(session: Session, container: AppContainer, item: ContentItem?, onBack: () -> Unit) {
    if (item == null) {
        ErrorState("No content selected.", onBack = onBack)
        return
    }

    val settings by container.sessionStore.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val streamUrlsState by produceState<List<String>?>(initialValue = null, session.accountKey, item.id, item.type, item.streamExtension, item.directSource, settings.streamUrlRedirectionCheck, settings.useExtensionInStreamUrl, settings.useM3uFormatInStreamUrl) {
        value = runCatching { container.contentRepository.resolvedStreamUrls(session, item, settings) }.getOrElse { emptyList() }
    }
    val streamUrls = streamUrlsState
    if (streamUrls == null) {
        LoadingState("Preparing stream...")
        return
    }
    if (streamUrls.isEmpty()) {
        ErrorState("No playable stream URL could be generated for this item.", onBack = onBack)
        return
    }

    val useExternal = when (item.type) {
        ContentType.LIVE, ContentType.EVENT -> settings.useExternalLivePlayer
        ContentType.MOVIE, ContentType.SERIES, ContentType.EPISODE -> settings.useExternalVodPlayer
    }
    if (useExternal) {
        ExternalPlayerLauncher(item = item, url = streamUrls.first(), onBack = onBack)
        return
    }

    InternalPlayerScreen(session, container, item, settings, streamUrls, onBack)
}

@Composable
private fun ExternalPlayerLauncher(item: ContentItem, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var launched by remember(url) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun openExternal() {
        error = runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open ${item.title}"))
        }.exceptionOrNull()?.let { ex ->
            if (ex is ActivityNotFoundException) "No external video player was found on this device." else ex.message ?: "Could not open external player."
        }
    }
    LaunchedEffect(url) {
        if (!launched) {
            launched = true
            openExternal()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).padding(16.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("External player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(item.title, color = PremiumMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { openExternal() }, modifier = Modifier.fillMaxWidth()) { Text("Open again") }
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

@Composable
private fun VlcVodPlayerScreen(session: Session, container: AppContainer, item: ContentItem, settings: AppSettings, streamUrls: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var urlIndex by remember(streamUrls) { mutableIntStateOf(0) }
    val streamUrl = streamUrls.getOrElse(urlIndex) { streamUrls.first() }
    val scope = rememberCoroutineScope()
    var resizeMode by remember { mutableStateOf(ResizeMode.Fit) }
    var locked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var gestureIndicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var vlcAudioChoices by remember { mutableStateOf(listOf(VlcTrackChoice(-2, "Auto / current"))) }
    var vlcSubtitleChoices by remember { mutableStateOf(listOf(VlcTrackChoice(-1, "Off", off = true))) }
    val vlcLayout = remember { VLCVideoLayout(context) }
    val vlcOptions = remember {
        arrayListOf(
            "--aout=opensles",
            "--audio-time-stretch",
            "--network-caching=2500",
            "--http-reconnect",
            "--no-drop-late-frames",
            "--no-skip-frames"
        )
    }
    val libVlc = remember(streamUrl) { LibVLC(context, vlcOptions) }
    val vlcPlayer = remember(streamUrl) { VlcMediaPlayer(libVlc) }

    DisposableEffect(activity, settings.forceLandscapeApp) {
        activity?.enterPlayerFullscreen()
        onDispose {
            activity?.exitPlayerFullscreen(settings.forceLandscapeApp)
            activity?.window?.let { window ->
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
        }
    }

    BackHandler(enabled = true) {
        if (!locked) onBack()
    }

    DisposableEffect(streamUrl) {
        error = null
        vlcPlayer.attachViews(vlcLayout, null, false, false)
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                VlcMediaPlayer.Event.Playing -> {
                    isPlaying = true
                    vlcAudioChoices = vlcPlayer.vlcAudioChoices()
                    vlcSubtitleChoices = vlcPlayer.vlcSubtitleChoices()
                }
                VlcMediaPlayer.Event.Paused, VlcMediaPlayer.Event.Stopped, VlcMediaPlayer.Event.EndReached -> isPlaying = false
                VlcMediaPlayer.Event.EncounteredError -> {
                    isPlaying = false
                    if (urlIndex < streamUrls.lastIndex) {
                        error = "VOD source failed. Trying alternate source ${urlIndex + 2}/${streamUrls.size}..."
                        urlIndex += 1
                    } else {
                        error = "This VOD could not be played internally. Try Open externally from the options menu."
                    }
                }
            }
        }
        val media = Media(libVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=2500")
            addOption(":http-reconnect")
            addOption(":audio-time-stretch")
            addOption(":codec=all")
            addOption(":avcodec-fast")
        }
        vlcPlayer.media = media
        media.release()
        vlcPlayer.play()
        onDispose {
            val lastPosition = runCatching { vlcPlayer.time.coerceAtLeast(0L) }.getOrDefault(0L)
            val lastDuration = runCatching { vlcPlayer.length.coerceAtLeast(0L) }.getOrDefault(0L)
            CoroutineScope(Dispatchers.IO).launch { container.libraryRepository.saveWatchProgress(session.accountKey, item, lastPosition, lastDuration) }
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.detachViews() }
            runCatching { vlcPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(item.id, streamUrl) {
        val history = container.libraryRepository.historyFor(session.accountKey, item)
        if (history != null && history.positionMs > 0) {
            delay(500)
            runCatching { vlcPlayer.time = history.positionMs }
        }
        container.libraryRepository.saveWatchProgress(session.accountKey, item, 0, 0)
    }

    LaunchedEffect(vlcPlayer) {
        while (true) {
            position = runCatching { vlcPlayer.time.coerceAtLeast(0L) }.getOrDefault(0L)
            duration = runCatching { vlcPlayer.length.coerceAtLeast(0L) }.getOrDefault(0L)
            isPlaying = runCatching { vlcPlayer.isPlaying }.getOrDefault(isPlaying)
            vlcAudioChoices = vlcPlayer.vlcAudioChoices()
            vlcSubtitleChoices = vlcPlayer.vlcSubtitleChoices()
            delay(700)
        }
    }

    LaunchedEffect(gestureIndicator) {
        if (gestureIndicator != null) {
            delay(900)
            gestureIndicator = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (!locked) overlay = !overlay else overlay = true },
                    onDoubleTap = { offset ->
                        if (!locked && duration > 0) {
                            val rewindMs = settings.rewindSkipSeconds.coerceIn(1, 600) * 1000L
                            val forwardMs = settings.forwardSkipSeconds.coerceIn(1, 600) * 1000L
                            val next = if (offset.x < size.width / 2) position - rewindMs else position + forwardMs
                            vlcPlayer.time = next.coerceIn(0L, duration)
                        }
                    }
                )
            }
            .pointerInput(locked) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (locked) return@detectVerticalDragGestures
                    val isLeft = change.position.x < size.width / 2
                    if (isLeft) {
                        val current = if (brightness >= 0f) brightness else (activity?.window?.attributes?.screenBrightness ?: 0.5f).let { if (it < 0f) 0.5f else it }
                        brightness = (current - dragAmount / 900f).coerceIn(0.05f, 1f)
                        activity?.window?.let { window ->
                            val attrs = window.attributes
                            attrs.screenBrightness = brightness
                            window.attributes = attrs
                        }
                        gestureIndicator = GestureIndicator("Brightness", brightness)
                    } else {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (cur - dragAmount / 90f).roundToInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        gestureIndicator = GestureIndicator("Volume", next.toFloat() / max.toFloat())
                    }
                }
            }
    ) {
        AndroidView(factory = { vlcLayout }, modifier = Modifier.fillMaxSize())

        if (overlay) {
            MinimalPlayerOverlay(
                item = item,
                position = position,
                duration = duration,
                resizeMode = resizeMode,
                locked = locked,
                urlIndex = urlIndex,
                urlCount = streamUrls.size,
                timeline = emptyList(),
                error = error,
                isPlaying = isPlaying,
                showSkipButtons = settings.showTimeSkipButtons,
                forwardSkipSeconds = settings.forwardSkipSeconds.coerceIn(1, 600),
                rewindSkipSeconds = settings.rewindSkipSeconds.coerceIn(1, 600),
                onOptions = { if (!locked) showOptions = true },
                onResize = { if (!locked) resizeMode = resizeMode.next() },
                onRotate = { if (!locked) toggleLandscape(context) },
                onLock = { locked = !locked; overlay = true },
                onPlayPause = { if (!locked) { if (vlcPlayer.isPlaying) vlcPlayer.pause() else vlcPlayer.play() } },
                onSeekBy = { by -> if (!locked && duration > 0) vlcPlayer.time = (position + by).coerceIn(0L, duration) },
                onSeek = { next -> if (!locked && duration > 0) vlcPlayer.time = next.coerceIn(0L, duration) },
                onRetry = {
                    error = null
                    if (urlIndex < streamUrls.lastIndex) urlIndex += 1 else {
                        runCatching { vlcPlayer.stop() }
                        runCatching { vlcPlayer.play() }
                    }
                }
            )
        }

        gestureIndicator?.let { GestureIndicatorView(it, Modifier.align(Alignment.Center)) }

        if (showOptions) {
            VlcVodOptionsDialog(
                item = item,
                settings = settings,
                streamUrls = streamUrls,
                urlIndex = urlIndex,
                audioChoices = vlcAudioChoices,
                subtitleChoices = vlcSubtitleChoices,
                onDismiss = { showOptions = false },
                onFavorite = { scope.launch { container.libraryRepository.toggleFavorite(session.accountKey, item) } },
                onSettingsChange = { next -> scope.launch { container.sessionStore.updateSettings(next) } },
                onAudioChoice = { choice ->
                    runCatching {
                        if (choice.off) vlcPlayer.setAudioTrack(-1) else if (choice.id >= 0) vlcPlayer.setAudioTrack(choice.id)
                    }.onFailure { error = it.message }
                },
                onSubtitleChoice = { choice ->
                    runCatching {
                        if (choice.off) vlcPlayer.setSpuTrack(-1) else if (choice.id >= 0) vlcPlayer.setSpuTrack(choice.id)
                    }.onFailure { error = it.message }
                    scope.launch { container.sessionStore.updateSettings(settings.copy(subtitlesEnabled = !choice.off)) }
                },
                onSource = { next -> urlIndex = next; showOptions = false },
                onOpenExternal = { runCatching { openExternalPlayer(context, streamUrl, item.title) }.onFailure { error = it.message } }
            )
        }
    }
}

@Composable
private fun VlcVodOptionsDialog(
    item: ContentItem,
    settings: AppSettings,
    streamUrls: List<String>,
    urlIndex: Int,
    audioChoices: List<VlcTrackChoice>,
    subtitleChoices: List<VlcTrackChoice>,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onAudioChoice: (VlcTrackChoice) -> Unit,
    onSubtitleChoice: (VlcTrackChoice) -> Unit,
    onSource: (Int) -> Unit,
    onOpenExternal: () -> Unit
) {
    var section by remember { mutableStateOf(PlayerOptionsSection.Main) }
    val title = when (section) {
        PlayerOptionsSection.Main -> "Player options"
        PlayerOptionsSection.Audio -> "Audio / dub"
        PlayerOptionsSection.Subtitles -> "Subtitles"
        PlayerOptionsSection.Quality -> "Quality / source"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (section != PlayerOptionsSection.Main) TextButton(onClick = { section = PlayerOptionsSection.Main }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = PremiumMuted)
                when (section) {
                    PlayerOptionsSection.Main -> {
                        OptionMenuRow("Audio / dub", "Choose audio track or dubbed language") { section = PlayerOptionsSection.Audio }
                        OptionMenuRow("Subtitles", "Choose embedded subtitles") { section = PlayerOptionsSection.Subtitles }
                        OptionMenuRow("Quality", "Choose Xtream source variant") { section = PlayerOptionsSection.Quality }
                        OptionMenuRow("Add/remove favorite", "Save this movie or episode locally") { onFavorite() }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) { Text("Open externally") }
                    }
                    PlayerOptionsSection.Audio -> {
                        audioChoices.forEach { choice ->
                            OptionMenuRow(choice.label, if (choice.off) "Disable audio" else "Use this audio track") { onAudioChoice(choice) }
                        }
                        if (audioChoices.size <= 1) Text("No separate audio/dub tracks were exposed by this VOD. The VLC engine still decodes wider audio formats such as AC3/EAC3/DTS when the device supports them.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    PlayerOptionsSection.Subtitles -> {
                        subtitleChoices.forEach { choice ->
                            OptionMenuRow(choice.label, if (choice.off) "Disable subtitles" else "Use this subtitle track") { onSubtitleChoice(choice) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Checkbox(checked = settings.subtitlesEnabled, onCheckedChange = { onSettingsChange(settings.copy(subtitlesEnabled = it)) })
                            Text("Enable subtitles")
                        }
                    }
                    PlayerOptionsSection.Quality -> {
                        streamUrls.forEachIndexed { index, url ->
                            OptionMenuRow(sourceLabel(url, index, index == urlIndex), if (index == urlIndex) "Current Xtream source" else "Switch source") { onSource(index) }
                        }
                        Text("For VOD, quality/source switching uses the available Xtream URL variants because most VOD files are direct files rather than adaptive live playlists.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

private data class VlcTrackChoice(val id: Int, val label: String, val off: Boolean = false)

private fun VlcMediaPlayer.vlcAudioChoices(): List<VlcTrackChoice> {
    val result = mutableListOf(VlcTrackChoice(-2, "Auto / current"))
    runCatching {
        audioTracks?.forEach { track ->
            val name = track.name?.takeIf { it.isNotBlank() } ?: "Audio ${track.id}"
            result += VlcTrackChoice(track.id, name)
        }
    }
    return result.distinctBy { it.id }
}

private fun VlcMediaPlayer.vlcSubtitleChoices(): List<VlcTrackChoice> {
    val result = mutableListOf(VlcTrackChoice(-1, "Off", off = true))
    runCatching {
        spuTracks?.forEach { track ->
            val name = track.name?.takeIf { it.isNotBlank() } ?: "Subtitle ${track.id}"
            result += VlcTrackChoice(track.id, name)
        }
    }
    return result.distinctBy { it.id }
}


@Composable
private fun InternalPlayerScreen(session: Session, container: AppContainer, item: ContentItem, settings: AppSettings, streamUrls: List<String>, onBack: () -> Unit) {
    val isVodLike = item.type == ContentType.MOVIE || item.type == ContentType.SERIES || item.type == ContentType.EPISODE
    if (isVodLike) {
        VlcVodPlayerScreen(session, container, item, settings, streamUrls, onBack)
    } else {
        Media3InternalPlayerScreen(session, container, item, settings, streamUrls, onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Media3InternalPlayerScreen(session: Session, container: AppContainer, item: ContentItem, settings: AppSettings, streamUrls: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var urlIndex by remember(streamUrls) { mutableIntStateOf(0) }
    val streamUrl = streamUrls.getOrElse(urlIndex) { streamUrls.first() }
    val scope = rememberCoroutineScope()
    var resizeMode by remember { mutableStateOf(ResizeMode.Fit) }
    var locked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var showOptions by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var gestureIndicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var subtitleChoices by remember { mutableStateOf(listOf(SubtitleChoice("auto", "Auto", false), SubtitleChoice("off", "Off", true))) }
    var audioChoices by remember { mutableStateOf(listOf(AudioChoice("auto", "Auto"), AudioChoice("off", "Off", off = true))) }
    var videoChoices by remember { mutableStateOf(listOf(VideoChoice("auto", "Auto"))) }
    val timelineState by produceState<List<EpgProgram>>(initialValue = emptyList(), session.accountKey, item.id, settings.liveTvEpg, settings.externalXmlTvUrl) {
        value = runCatching { container.contentRepository.timeline(session, item, settings, 6) }.getOrDefault(emptyList())
    }

    DisposableEffect(activity, settings.forceLandscapeApp) {
        activity?.enterPlayerFullscreen()
        onDispose {
            activity?.exitPlayerFullscreen(settings.forceLandscapeApp)
            activity?.window?.let { window ->
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
        }
    }


    BackHandler(enabled = true) {
        if (!locked) onBack()
    }

    val mediaItem = remember(streamUrl, settings.externalSubtitleUrl, settings.preferredSubtitleLanguage, settings.subtitlesEnabled) {
        buildMediaItem(streamUrl, settings, item)
    }

    val player = remember(streamUrl, mediaItem) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RedPlusIPTV/1.3")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                volume = 1f
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player, streamUrls, urlIndex) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackException: PlaybackException) {
                if (urlIndex < streamUrls.lastIndex) {
                    error = "Stream failed. Trying alternate source ${urlIndex + 2}/${streamUrls.size}..."
                    urlIndex += 1
                } else {
                    error = playbackException.message ?: "Playback error. Check that your provider supports this stream."
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                subtitleChoices = player.subtitleChoices()
                audioChoices = player.audioChoices()
                videoChoices = player.videoChoices(streamUrls, urlIndex)
                player.ensureAudioEnabled()
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        subtitleChoices = player.subtitleChoices()
        audioChoices = player.audioChoices()
        videoChoices = player.videoChoices(streamUrls, urlIndex)
        onDispose {
            val lastPosition = player.currentPosition.coerceAtLeast(0L)
            val rawDuration = player.duration
            val lastDuration = if (rawDuration > 0) rawDuration else 0L
            CoroutineScope(Dispatchers.IO).launch { container.libraryRepository.saveWatchProgress(session.accountKey, item, lastPosition, lastDuration) }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, settings.subtitlesEnabled, settings.preferredSubtitleLanguage) {
        player.applySubtitleSettings(settings)
        player.ensureAudioEnabled()
    }

    LaunchedEffect(item.id, streamUrl) {
        error = null
        val history = container.libraryRepository.historyFor(session.accountKey, item)
        if (history != null && item.type != ContentType.LIVE && item.type != ContentType.EVENT && history.positionMs > 0) {
            player.seekTo(history.positionMs)
        }
        container.libraryRepository.saveWatchProgress(session.accountKey, item, 0, 0)
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = if (player.duration > 0) player.duration else 0L
            delay(700)
        }
    }

    LaunchedEffect(gestureIndicator) {
        if (gestureIndicator != null) {
            delay(900)
            gestureIndicator = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (!locked) overlay = !overlay else overlay = true },
                    onDoubleTap = { offset ->
                        if (!locked && player.isCurrentMediaItemSeekable) {
                            val rewindMs = settings.rewindSkipSeconds.coerceIn(1, 600) * 1000L
                            val forwardMs = settings.forwardSkipSeconds.coerceIn(1, 600) * 1000L
                            if (offset.x < size.width / 2) player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0))
                            else player.seekTo(player.currentPosition + forwardMs)
                        }
                    }
                )
            }
            .pointerInput(locked) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (locked) return@detectVerticalDragGestures
                    val isLeft = change.position.x < size.width / 2
                    if (isLeft) {
                        val current = if (brightness >= 0f) brightness else (activity?.window?.attributes?.screenBrightness ?: 0.5f).let { if (it < 0f) 0.5f else it }
                        brightness = (current - dragAmount / 900f).coerceIn(0.05f, 1f)
                        activity?.window?.let { window ->
                            val attrs = window.attributes
                            attrs.screenBrightness = brightness
                            window.attributes = attrs
                        }
                        gestureIndicator = GestureIndicator("Brightness", brightness)
                    } else {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (cur - dragAmount / 90f).roundToInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        gestureIndicator = GestureIndicator("Volume", next.toFloat() / max.toFloat())
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    keepScreenOn = true
                    useController = false
                    this.player = player
                    this.resizeMode = resizeMode.media3
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode.media3
                view.useController = false
            },
            modifier = Modifier.fillMaxSize()
        )

        if (overlay) {
            MinimalPlayerOverlay(
                item = item,
                position = position,
                duration = duration,
                resizeMode = resizeMode,
                locked = locked,
                urlIndex = urlIndex,
                urlCount = streamUrls.size,
                timeline = timelineState,
                error = error,
                isPlaying = isPlaying,
                showSkipButtons = settings.showTimeSkipButtons,
                forwardSkipSeconds = settings.forwardSkipSeconds.coerceIn(1, 600),
                rewindSkipSeconds = settings.rewindSkipSeconds.coerceIn(1, 600),
                onOptions = { if (!locked) showOptions = true },
                onResize = { if (!locked) resizeMode = resizeMode.next() },
                onRotate = { if (!locked) toggleLandscape(context) },
                onLock = { locked = !locked; overlay = true },
                onPlayPause = { if (!locked) { if (player.isPlaying) player.pause() else player.play() } },
                onSeekBy = { by -> if (!locked && player.isCurrentMediaItemSeekable) player.seekTo((player.currentPosition + by).coerceAtLeast(0L)) },
                onSeek = { next -> if (!locked && player.isCurrentMediaItemSeekable) player.seekTo(next) },
                onRetry = {
                    error = null
                    if (urlIndex < streamUrls.lastIndex) urlIndex += 1 else {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            )
        }

        gestureIndicator?.let { GestureIndicatorView(it, Modifier.align(Alignment.Center)) }

        if (showOptions) {
            PlayerOptionsDialog(
                item = item,
                settings = settings,
                audioChoices = audioChoices,
                videoChoices = videoChoices,
                subtitleChoices = subtitleChoices,
                onDismiss = { showOptions = false },
                onFavorite = { scope.launch { container.libraryRepository.toggleFavorite(session.accountKey, item) } },
                onSettingsChange = { next -> scope.launch { container.sessionStore.updateSettings(next) } },
                onSubtitleChoice = { choice ->
                    player.applySubtitleChoice(choice)
                    scope.launch { container.sessionStore.updateSettings(settings.copy(subtitlesEnabled = !choice.off, preferredSubtitleLanguage = choice.language)) }
                },
                onAudioChoice = { choice ->
                    player.applyAudioChoice(choice)
                },
                onVideoChoice = { choice ->
                    if (choice.sourceIndex != null) {
                        urlIndex = choice.sourceIndex
                    } else {
                        player.applyVideoChoice(choice)
                    }
                },
                onOpenExternal = { runCatching { openExternalPlayer(context, streamUrl, item.title) }.onFailure { error = it.message } }
            )
        }
    }
}

@Composable
private fun MinimalPlayerOverlay(
    item: ContentItem,
    position: Long,
    duration: Long,
    resizeMode: ResizeMode,
    locked: Boolean,
    urlIndex: Int,
    urlCount: Int,
    timeline: List<EpgProgram>,
    error: String?,
    isPlaying: Boolean,
    showSkipButtons: Boolean,
    forwardSkipSeconds: Int,
    rewindSkipSeconds: Int,
    onOptions: () -> Unit,
    onResize: () -> Unit,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit
) {
    val isVod = item.type != ContentType.LIVE && item.type != ContentType.EVENT
    Box(Modifier.fillMaxSize().padding(10.dp)) {
        GlassPanel(Modifier.align(Alignment.TopStart).fillMaxWidth(.68f)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playbackSubtitle(item, position, duration, urlIndex, urlCount, resizeMode), color = PremiumMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!locked) {
                TextButton(
                    onClick = onRotate,
                    modifier = Modifier.background(Color.Black.copy(alpha = .44f), RoundedCornerShape(12.dp))
                ) { Text("↻", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
            IconButton(
                onClick = onLock,
                modifier = Modifier.background(Color.Black.copy(alpha = .50f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    painter = painterResource(if (locked) R.drawable.redplus_lock else R.drawable.redplus_unlock),
                    contentDescription = if (locked) "Unlock controls" else "Lock controls",
                    tint = Color.Unspecified,
                    modifier = Modifier.width(24.dp).height(24.dp)
                )
            }
        }

        if (!locked) {
            Row(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .38f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSkipButtons && isVod) {
                    TextButton(onClick = { onSeekBy(-rewindSkipSeconds.coerceIn(1, 600) * 1000L) }) { Text("-${rewindSkipSeconds}s") }
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.background(PremiumRed.copy(alpha = .92f), RoundedCornerShape(50))) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.White)
                }
                if (showSkipButtons && isVod) {
                    TextButton(onClick = { onSeekBy(forwardSkipSeconds.coerceIn(1, 600) * 1000L) }) { Text("+${forwardSkipSeconds}s") }
                }
            }
        }

        if (timeline.isNotEmpty() && !locked) {
            GlassPanel(Modifier.align(Alignment.BottomStart).fillMaxWidth(.68f).padding(bottom = if (isVod && duration > 0) 58.dp else 0.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Schedule", color = PremiumRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    timeline.take(2).forEach { program ->
                        Text("${program.timeText()}  ${program.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (isVod && duration > 0) {
            GlassPanel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 54.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { if (!locked) onSeek(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        enabled = !locked
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(msToTime(position), color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                        Text(msToTime(duration), color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (error != null && !locked) {
            GlassPanel(Modifier.align(Alignment.Center).fillMaxWidth(.92f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (!locked) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onResize,
                    modifier = Modifier.border(BorderStroke(1.4.dp, Color.White.copy(alpha = .78f)), RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .36f), RoundedCornerShape(12.dp))
                ) { Text("□ ${resizeMode.label}", color = Color.White, style = MaterialTheme.typography.bodySmall) }
                IconButton(
                    onClick = onOptions,
                    modifier = Modifier.background(Color.Black.copy(alpha = .46f), RoundedCornerShape(12.dp))
                ) { Icon(Icons.Outlined.MoreVert, contentDescription = "Options", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun GestureIndicatorView(indicator: GestureIndicator, modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(220.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(indicator.label, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = indicator.value.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Text("${(indicator.value.coerceIn(0f, 1f) * 100).roundToInt()}%", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerOptionsDialog(
    item: ContentItem,
    settings: AppSettings,
    audioChoices: List<AudioChoice>,
    videoChoices: List<VideoChoice>,
    subtitleChoices: List<SubtitleChoice>,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onSubtitleChoice: (SubtitleChoice) -> Unit,
    onAudioChoice: (AudioChoice) -> Unit,
    onVideoChoice: (VideoChoice) -> Unit,
    onOpenExternal: () -> Unit
) {
    var section by remember { mutableStateOf(PlayerOptionsSection.Main) }
    val title = when (section) {
        PlayerOptionsSection.Main -> "Player options"
        PlayerOptionsSection.Audio -> "Audio / dub"
        PlayerOptionsSection.Subtitles -> "Subtitles"
        PlayerOptionsSection.Quality -> "Quality / source"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (section != PlayerOptionsSection.Main) TextButton(onClick = { section = PlayerOptionsSection.Main }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = PremiumMuted)
                when (section) {
                    PlayerOptionsSection.Main -> {
                        OptionMenuRow("Audio / dub", "Choose audio track or dubbed language") { section = PlayerOptionsSection.Audio }
                        OptionMenuRow("Subtitles", "Choose embedded/external subtitles") { section = PlayerOptionsSection.Subtitles }
                        OptionMenuRow("Quality", "Choose adaptive quality or Xtream source variant") { section = PlayerOptionsSection.Quality }
                        OptionMenuRow("Add/remove favorite", "Save this channel, movie, or episode locally") { onFavorite() }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) { Text("Open externally") }
                    }
                    PlayerOptionsSection.Audio -> {
                        audioChoices.take(24).forEach { choice ->
                            OptionMenuRow(choice.label, if (choice.off) "Disable audio" else "Use this audio track") { onAudioChoice(choice) }
                        }
                        if (audioChoices.size <= 2) Text("No extra audio/dub tracks were exposed by this stream. If a VOD has unsupported AC3/DTS audio, use Open externally with a player that supports that codec.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    PlayerOptionsSection.Subtitles -> {
                        subtitleChoices.take(24).forEach { choice ->
                            OptionMenuRow(choice.label, if (choice.off) "Disable subtitles" else "Use this subtitle track") { onSubtitleChoice(choice) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Checkbox(checked = settings.subtitlesEnabled, onCheckedChange = { onSettingsChange(settings.copy(subtitlesEnabled = it)) })
                            Text("Enable embedded/external subtitles")
                        }
                    }
                    PlayerOptionsSection.Quality -> {
                        videoChoices.take(28).forEach { choice ->
                            OptionMenuRow(choice.label, if (choice.sourceIndex != null) "Xtream source URL" else "Adaptive video track") { onVideoChoice(choice) }
                        }
                        Text("Auto uses adaptive HLS quality when available. Source buttons switch between generated Xtream URL variants such as HLS/m3u8, TS, MP4/default.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun OptionMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().background(PremiumPanel.copy(alpha = .72f), RoundedCornerShape(14.dp)).padding(vertical = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PremiumMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun buildMediaItem(url: String, settings: AppSettings, item: ContentItem): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    streamMimeType(url)?.let { builder.setMimeType(it) }
    if (settings.subtitlesEnabled && settings.externalSubtitleUrl.isNotBlank()) {
        val subtitleUri = Uri.parse(settings.externalSubtitleUrl.trim())
        val mime = when (subtitleUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(mime)
                    .setLanguage(settings.preferredSubtitleLanguage.takeUnless { it == "auto" || it == "off" })
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
    }
    return builder.build()
}

private fun Player.applySubtitleSettings(settings: AppSettings) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled || settings.preferredSubtitleLanguage == "off")
        .apply { if (settings.preferredSubtitleLanguage !in listOf("auto", "off")) setPreferredTextLanguage(settings.preferredSubtitleLanguage) }
        .build()
}

private fun Player.applySubtitleChoice(choice: SubtitleChoice) {
    val builder: TrackSelectionParameters.Builder = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, choice.off)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
    if (!choice.off && choice.language != "auto") builder.setPreferredTextLanguage(choice.language)
    trackSelectionParameters = builder.build()
}

private fun Player.ensureAudioEnabled() {
    volume = 1f
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .build()
}

@OptIn(UnstableApi::class)
private fun Player.applyAudioChoice(choice: AudioChoice) {
    val builder = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, choice.off)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
    val group = choice.trackGroup
    val index = choice.trackIndex
    if (!choice.off && group != null && index != null) {
        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
    }
    trackSelectionParameters = builder.build()
    if (!choice.off) volume = 1f
}

@OptIn(UnstableApi::class)
private fun Player.applyVideoChoice(choice: VideoChoice) {
    val builder = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    val group = choice.trackGroup
    val index = choice.trackIndex
    if (group != null && index != null) {
        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
    }
    trackSelectionParameters = builder.build()
}

private fun Player.subtitleChoices(): List<SubtitleChoice> {
    val result = linkedMapOf<String, SubtitleChoice>()
    result["auto"] = SubtitleChoice("auto", "Auto", false)
    result["off"] = SubtitleChoice("off", "Off", true)
    currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
        for (i in 0 until group.length) {
            if (group.isTrackSupported(i)) {
                val format = group.getTrackFormat(i)
                val language = format.language?.takeIf { it.isNotBlank() } ?: "auto"
                val label = listOfNotNull(format.label, format.language).joinToString(" • ").ifBlank { "Subtitle ${result.size}" }
                result[language + label] = SubtitleChoice(language, label, false)
            }
        }
    }
    return result.values.toList()
}

@OptIn(UnstableApi::class)
private fun Player.audioChoices(): List<AudioChoice> {
    val result = mutableListOf(AudioChoice("auto", "Auto"), AudioChoice("off", "Off", off = true))
    currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEachIndexed { groupIndex, group ->
        for (i in 0 until group.length) {
            if (group.isTrackSupported(i)) {
                val format = group.getTrackFormat(i)
                val language = format.language?.takeIf { it.isNotBlank() } ?: "und"
                val labelParts = mutableListOf<String>()
                if (!format.label.isNullOrBlank()) labelParts += format.label!!
                if (language != "und") labelParts += language.uppercase()
                if (format.channelCount > 0) labelParts += "${format.channelCount}ch"
                if (!format.sampleMimeType.isNullOrBlank()) labelParts += format.sampleMimeType!!.substringAfterLast('.')
                val label = labelParts.joinToString(" • ").ifBlank { "Audio ${result.size - 1}" }
                result += AudioChoice("audio_${groupIndex}_$i", label, group, i)
            }
        }
    }
    return result.distinctBy { it.id }
}

@OptIn(UnstableApi::class)
private fun Player.videoChoices(streamUrls: List<String>, urlIndex: Int): List<VideoChoice> {
    val result = mutableListOf(VideoChoice("auto", "Auto"))
    if (streamUrls.size > 1) {
        streamUrls.forEachIndexed { index, url ->
            result += VideoChoice("source_$index", sourceLabel(url, index, index == urlIndex), sourceIndex = index)
        }
    }
    currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEachIndexed { groupIndex, group ->
        for (i in 0 until group.length) {
            if (group.isTrackSupported(i)) {
                val format = group.getTrackFormat(i)
                val resolution = when {
                    format.height > 0 -> "${format.height}p"
                    format.width > 0 -> "${format.width}w"
                    !format.label.isNullOrBlank() -> format.label!!
                    else -> "Video ${result.size}"
                }
                val bitrate = if (format.bitrate > 0) " • ${format.bitrate / 1000} kbps" else ""
                result += VideoChoice("track_${groupIndex}_$i", "$resolution$bitrate", group, i)
            }
        }
    }
    return result.distinctBy { it.id }
}

private fun sourceLabel(url: String, index: Int, selected: Boolean): String {
    val lower = url.lowercase()
    val type = when {
        ".m3u8" in lower -> "HLS/m3u8"
        ".ts" in lower -> "TS"
        ".mp4" in lower -> "MP4"
        ".mkv" in lower -> "MKV"
        else -> "Default"
    }
    return "Source ${index + 1} • $type${if (selected) " ✓" else ""}"
}

private data class SubtitleChoice(val language: String, val label: String, val off: Boolean)

@OptIn(UnstableApi::class)
private data class AudioChoice(
    val id: String,
    val label: String,
    val trackGroup: Tracks.Group? = null,
    val trackIndex: Int? = null,
    val off: Boolean = false
)

@OptIn(UnstableApi::class)
private data class VideoChoice(
    val id: String,
    val label: String,
    val trackGroup: Tracks.Group? = null,
    val trackIndex: Int? = null,
    val sourceIndex: Int? = null
)

private data class GestureIndicator(val label: String, val value: Float)

private enum class PlayerOptionsSection { Main, Audio, Subtitles, Quality }

private fun streamMimeType(url: String): String? {
    val clean = url.substringBefore('?').lowercase()
    return when {
        clean.endsWith(".m3u8") -> "application/x-mpegURL"
        clean.endsWith(".ts") -> "video/mp2t"
        clean.endsWith(".mp4") || clean.endsWith(".m4v") -> "video/mp4"
        clean.endsWith(".mkv") -> "video/x-matroska"
        else -> null
    }
}

private fun playbackSubtitle(item: ContentItem, position: Long, duration: Long, urlIndex: Int, urlCount: Int, resizeMode: ResizeMode): String {
    val source = if (urlCount > 1) " • Source ${urlIndex + 1}/$urlCount" else ""
    val mode = " • ${resizeMode.label}"
    return if (item.type == ContentType.LIVE || item.type == ContentType.EVENT) "Live playback$source$mode" else "${msToTime(position)} / ${msToTime(duration)}$source$mode"
}

private fun EpgProgram.timeText(): String {
    val start = unixToLocalTime(startTimestamp)
    val stop = unixToLocalTime(stopTimestamp)
    return if (start.isBlank() && stop.isBlank()) "" else "$start-$stop"
}

private fun openExternalPlayer(context: Context, url: String, title: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open $title"))
}

private fun toggleLandscape(context: Context) {
    val activity = context as? Activity ?: return
    activity.requestedOrientation = if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

private fun Activity.enterPlayerFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.exitPlayerFullscreen(forceLandscapeApp: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    WindowCompat.setDecorFitsSystemWindows(window, true)
    requestedOrientation = if (forceLandscapeApp) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private enum class ResizeMode(val label: String, val media3: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
    fun next(): ResizeMode = entries[(ordinal + 1) % entries.size]
}
