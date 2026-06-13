package com.redplus.iptv.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.redplus.iptv.MainActivity
import com.redplus.iptv.R
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import kotlin.math.max

class AudioOnlyPlaybackService : Service() {
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var title: String = "RedPlus audio"
    private var isVod: Boolean = false
    private var streamUrl: String = ""
    private var item: ContentItem? = null
    private var lastError: String? = null

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPlayback(intent)
            ACTION_PLAY_PAUSE -> togglePlayback()
            ACTION_STOP -> stopPlayback()
            else -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep audio running when the user leaves the app. The notification remains the control surface.
        super.onTaskRemoved(rootIntent)
    }

    private fun startPlayback(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) return

        streamUrl = url
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "RedPlus audio" }
        isVod = intent.getBooleanExtra(EXTRA_IS_VOD, false)
        item = itemFromIntent(intent)
        lastError = null
        val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)

        startForeground(NOTIFICATION_ID, buildNotification(playing = false, forceStarting = true))
        releasePlayback(keepForeground = true)
        acquireWakeLock()

        val options = arrayListOf(
            "--aout=opensles",
            "--audio-time-stretch",
            "--network-caching=2500",
            "--http-reconnect",
            "--no-video",
            "--no-video-title-show"
        )
        val vlc = LibVLC(this, options)
        val mediaPlayer = MediaPlayer(vlc)
        libVlc = vlc
        player = mediaPlayer

        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing,
                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped,
                MediaPlayer.Event.Opening,
                MediaPlayer.Event.Buffering -> updateNotification()
                MediaPlayer.Event.EncounteredError -> {
                    lastError = "Audio playback failed"
                    updateNotification()
                }
                MediaPlayer.Event.EndReached -> stopPlayback()
            }
        }

        val media = Media(vlc, Uri.parse(url)).apply {
            addOption(":no-video")
            addOption(":network-caching=2500")
            addOption(":http-reconnect")
            addOption(":audio-time-stretch")
            addOption(":codec=all")
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
        if (isVod && position > 0L) {
            handler.postDelayed({ runCatching { mediaPlayer.time = position } }, 650L)
        }
        handler.removeCallbacks(progressUpdater)
        handler.post(progressUpdater)
        updateNotification()
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        updateNotification()
    }

    private fun stopPlayback() {
        releasePlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayback(keepForeground: Boolean = false) {
        handler.removeCallbacks(progressUpdater)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        runCatching { libVlc?.release() }
        player = null
        libVlc = null
        if (!keepForeground) releaseWakeLock()
    }

    private fun updateNotification() {
        val notification = buildNotification(playing = player?.isPlaying == true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(playing: Boolean, forceStarting: Boolean = false): Notification {
        val openPending = PendingIntent.getActivity(this, 7001, buildOpenIntent(), pendingFlags())
        val playPauseIntent = Intent(this, AudioOnlyPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE)
        val playPausePending = PendingIntent.getService(this, 7002, playPauseIntent, pendingFlags())
        val stopIntent = Intent(this, AudioOnlyPlaybackService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 7003, stopIntent, pendingFlags())

        val durationMs = player?.length?.takeIf { it > 0L } ?: 0L
        val positionMs = player?.time?.coerceAtLeast(0L) ?: 0L
        val statusText = when {
            lastError != null -> lastError!!
            forceStarting -> "Starting audio..."
            isVod && durationMs > 0L -> "${formatTime(positionMs)} / ${formatTime(durationMs)}"
            isVod -> "Audio only"
            else -> "Live audio"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(openPending)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", playPausePending)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)

        if (isVod && durationMs > 0L) {
            builder.setProgress((durationMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), (positionMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun buildOpenIntent(): Intent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_STREAM
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_URL, streamUrl)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_IS_VOD, isVod)
        }
        item?.let { putItemExtras(intent, it) }
        return intent
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedPlusIPTV:AudioOnly").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun pendingFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun formatTime(ms: Long): String {
        val totalSeconds = max(0L, ms / 1000L)
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val CHANNEL_ID = "redplus_audio_playback"
        private const val NOTIFICATION_ID = 8401
        const val ACTION_START = "com.redplus.iptv.audio.START"
        const val ACTION_PLAY_PAUSE = "com.redplus.iptv.audio.PLAY_PAUSE"
        const val ACTION_STOP = "com.redplus.iptv.audio.STOP"
        const val ACTION_OPEN_STREAM = "com.redplus.iptv.audio.OPEN_STREAM"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_VOD = "is_vod"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_TYPE = "item_type"
        const val EXTRA_ITEM_IMAGE = "item_image"
        const val EXTRA_ITEM_CATEGORY = "item_category"
        const val EXTRA_ITEM_EXT = "item_ext"
        const val EXTRA_ITEM_DIRECT = "item_direct"
        const val EXTRA_ITEM_EPG = "item_epg"

        fun start(context: Context, url: String, item: ContentItem, isVod: Boolean, positionMs: Long) {
            val intent = Intent(context, AudioOnlyPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, item.title)
                putExtra(EXTRA_IS_VOD, isVod)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putItemExtras(this, item)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioOnlyPlaybackService::class.java).setAction(ACTION_STOP))
        }

        fun putItemExtras(intent: Intent, item: ContentItem) {
            intent.putExtra(EXTRA_ITEM_ID, item.id)
            intent.putExtra(EXTRA_ITEM_TYPE, item.type.name)
            intent.putExtra(EXTRA_TITLE, item.title)
            intent.putExtra(EXTRA_ITEM_IMAGE, item.image)
            intent.putExtra(EXTRA_ITEM_CATEGORY, item.categoryId)
            intent.putExtra(EXTRA_ITEM_EXT, item.streamExtension)
            intent.putExtra(EXTRA_ITEM_DIRECT, item.directSource)
            intent.putExtra(EXTRA_ITEM_EPG, item.epgChannelId)
        }

        fun itemFromIntent(intent: Intent?): ContentItem? {
            if (intent == null) return null
            val id = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
            val typeName = intent.getStringExtra(EXTRA_ITEM_TYPE).orEmpty()
            val type = runCatching { ContentType.valueOf(typeName) }.getOrNull() ?: return null
            val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { return null }
            return ContentItem(
                id = id,
                type = type,
                title = title,
                image = intent.getStringExtra(EXTRA_ITEM_IMAGE),
                categoryId = intent.getStringExtra(EXTRA_ITEM_CATEGORY),
                streamExtension = intent.getStringExtra(EXTRA_ITEM_EXT),
                directSource = intent.getStringExtra(EXTRA_ITEM_DIRECT),
                epgChannelId = intent.getStringExtra(EXTRA_ITEM_EPG)
            )
        }
    }
}
