package com.redplus.iptv.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.ui.ErrorState
import com.redplus.iptv.ui.GlassPanel
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.util.msToTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(session: Session, container: AppContainer, item: ContentItem?, onBack: () -> Unit) {
    if (item == null) {
        ErrorState("No content selected.", onBack = onBack)
        return
    }

    val context = LocalContext.current
    val streamUrls = remember(session.accountKey, item.id, item.type, item.streamExtension, item.directSource) {
        container.contentRepository.buildStreamUrls(session, item)
    }
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

    val player = remember(streamUrl) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RedPlusIPTV/1.0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackException: PlaybackException) {
                if (urlIndex < streamUrls.lastIndex) {
                    error = "Stream failed. Trying alternate format ${urlIndex + 2}/${streamUrls.size}..."
                    urlIndex += 1
                } else {
                    error = playbackException.message ?: "Playback error. Check that your provider supports this stream."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val lastPosition = player.currentPosition.coerceAtLeast(0L)
            val rawDuration = player.duration
            val lastDuration = if (rawDuration > 0) rawDuration else 0L
            scope.launch { container.libraryRepository.saveWatchProgress(session.accountKey, item, lastPosition, lastDuration) }
            player.removeListener(listener)
            player.release()
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (!locked) overlay = !overlay },
                    onDoubleTap = { offset ->
                        if (!locked && player.isCurrentMediaItemSeekable) {
                            if (offset.x < size.width / 2) player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            else player.seekTo(player.currentPosition + 10_000)
                        }
                    }
                )
            }
            .pointerInput(locked) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (locked) return@detectVerticalDragGestures
                    val isLeft = change.position.x < size.width / 2
                    if (isLeft) {
                        val activity = context as? Activity
                        val current = if (brightness >= 0f) brightness else (activity?.window?.attributes?.screenBrightness ?: 0.5f).let { if (it < 0f) 0.5f else it }
                        brightness = (current - dragAmount / 900f).coerceIn(0.05f, 1f)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightness }
                    } else {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (cur - dragAmount / 90f).toInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    keepScreenOn = true
                    useController = true
                    this.player = player
                    this.resizeMode = resizeMode.media3
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode.media3
                view.useController = !locked
            },
            modifier = Modifier.fillMaxSize()
        )

        if (overlay) {
            PlayerOverlay(
                item = item,
                position = position,
                duration = duration,
                resizeMode = resizeMode,
                locked = locked,
                error = error,
                urlIndex = urlIndex,
                urlCount = streamUrls.size,
                onBack = onBack,
                onRetry = {
                    error = null
                    if (urlIndex < streamUrls.lastIndex) urlIndex += 1 else {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = true
                    }
                },
                onResize = { resizeMode = resizeMode.next() },
                onLock = { locked = !locked },
                onFavorite = { scope.launch { container.libraryRepository.toggleFavorite(session.accountKey, item) } },
                onSeek = { player.seekTo(it) }
            )
        }
    }
}

@Composable
private fun PlayerOverlay(
    item: ContentItem,
    position: Long,
    duration: Long,
    resizeMode: ResizeMode,
    locked: Boolean,
    error: String?,
    urlIndex: Int,
    urlCount: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onResize: () -> Unit,
    onLock: () -> Unit,
    onFavorite: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        GlassPanel(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
                if (maxWidth < 620.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onBack) { Text("Back") }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(playbackSubtitle(item, position, duration, urlIndex, urlCount), color = PremiumMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = onResize, label = { Text(resizeMode.label) })
                            AssistChip(onClick = onFavorite, label = { Text("Fav") })
                            AssistChip(onClick = onLock, label = { Text(if (locked) "Unlock" else "Lock") })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("Back") }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(playbackSubtitle(item, position, duration, urlIndex, urlCount), color = PremiumMuted)
                        }
                        AssistChip(onClick = onResize, label = { Text(resizeMode.label) })
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onFavorite) { Text("Favorite") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onLock) { Text(if (locked) "Unlock" else "Lock") }
                    }
                }
            }
        }

        if (error != null) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (duration > 0 && item.type != ContentType.LIVE && item.type != ContentType.EVENT) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Slider(value = position.toFloat().coerceIn(0f, duration.toFloat()), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.toFloat())
                    Text("Double tap to seek. Swipe left/right side for brightness/volume.", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("Tap to show/hide controls. Double tap seeks when supported. Swipe for brightness or volume.", Modifier.padding(10.dp), color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun playbackSubtitle(item: ContentItem, position: Long, duration: Long, urlIndex: Int, urlCount: Int): String {
    val stream = if (urlCount > 1) " • Source ${urlIndex + 1}/$urlCount" else ""
    return if (item.type == ContentType.LIVE || item.type == ContentType.EVENT) "Live playback$stream" else "${msToTime(position)} / ${msToTime(duration)}$stream"
}

private enum class ResizeMode(val label: String, val media3: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
    fun next(): ResizeMode = entries[(ordinal + 1) % entries.size]
}
