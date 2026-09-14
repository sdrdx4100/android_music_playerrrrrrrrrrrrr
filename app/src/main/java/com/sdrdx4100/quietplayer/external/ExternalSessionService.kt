package com.sdrdx4100.quietplayer.external

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalQueueItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val artwork: Bitmap?,
)

data class ExternalSessionState(
    val connected: Boolean = false,
    val packageName: String? = null,
    val appName: String? = null,
    val title: String = "Nothing playing",
    val artist: String = "Start playback in Apple Music or another media app",
    val album: String = "",
    val artwork: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val updateTimeMs: Long = 0L,
    val speed: Float = 0f,
    val isPlaying: Boolean = false,
    val actions: Long = 0L,
    val queue: List<ExternalQueueItem> = emptyList(),
    val currentQueueId: Long = -1L,
    val accentColor: Int = 0xFF60736B.toInt(),
) {
    fun estimatedPosition(nowMs: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val elapsed = if (isPlaying) ((nowMs - updateTimeMs) * speed).toLong() else 0L
        return (positionMs + elapsed).coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
}

object ExternalSessionBridge {
    private val _state = MutableStateFlow(ExternalSessionState())
    val state = _state.asStateFlow()
    private var controller: MediaController? = null

    internal fun attach(value: MediaController?, context: Context) {
        controller = value
        publish(context)
    }

    internal fun publish(context: Context) {
        val active = controller
        if (active == null) {
            _state.value = ExternalSessionState()
            return
        }
        val metadata = active.metadata
        val playback = active.playbackState
        val packageName = active.packageName
        val appName = knownAppName(packageName) ?: runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        _state.value = ExternalSessionState(
            connected = true,
            packageName = packageName,
            appName = appName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown title" },
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown artist" },
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            artwork = artwork,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L,
            updateTimeMs = playback?.lastPositionUpdateTime ?: 0L,
            speed = playback?.playbackSpeed ?: 0f,
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            actions = playback?.actions ?: 0L,
            currentQueueId = playback?.activeQueueItemId ?: -1L,
            accentColor = artwork?.let(::mutedArtworkColor) ?: 0xFF60736B.toInt(),
            queue = active.queue.orEmpty().map { item ->
                ExternalQueueItem(
                    id = item.queueId,
                    title = item.description.title?.toString().orEmpty(),
                    subtitle = item.description.subtitle?.toString().orEmpty(),
                    artwork = item.description.iconBitmap,
                )
            },
        )
    }

    fun togglePlay() = controller?.let {
        if (_state.value.isPlaying) it.transportControls.pause() else it.transportControls.play()
    }
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun next() = controller?.transportControls?.skipToNext()
    fun seekTo(positionMs: Long) = controller?.transportControls?.seekTo(positionMs)
    fun playQueueItem(id: Long) = controller?.transportControls?.skipToQueueItem(id)

    private fun knownAppName(packageName: String): String? = when (packageName) {
        "com.apple.android.music" -> "Apple Music"
        "com.google.android.apps.youtube.music" -> "YouTube Music"
        "com.amazon.mp3" -> "Amazon Music"
        "com.spotify.music" -> "Spotify"
        else -> null
    }

    private fun mutedArtworkColor(bitmap: Bitmap): Int {
        val source = Palette.from(bitmap).maximumColorCount(12).generate().dominantSwatch?.rgb
            ?: return 0xFF60736B.toInt()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(source, hsl)
        hsl[1] = hsl[1].coerceIn(.16f, .34f)
        hsl[2] = hsl[2].coerceIn(.20f, .36f)
        return ColorUtils.HSLToColor(hsl)
    }
}

class ExternalSessionService : NotificationListenerService() {
    private lateinit var sessionManager: MediaSessionManager
    private var activeController: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = ExternalSessionBridge.publish(this@ExternalSessionService)
        override fun onPlaybackStateChanged(state: PlaybackState?) = ExternalSessionBridge.publish(this@ExternalSessionService)
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = ExternalSessionBridge.publish(this@ExternalSessionService)
        override fun onSessionDestroyed() = refreshSessions()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions(it) }

    override fun onListenerConnected() {
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, ExternalSessionService::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, component)
        refreshSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshSessions()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshSessions()

    private fun refreshSessions(sessions: List<MediaController>? = null) {
        if (!::sessionManager.isInitialized) return
        val available = sessions ?: runCatching {
            sessionManager.getActiveSessions(ComponentName(this, ExternalSessionService::class.java))
        }.getOrDefault(emptyList())
        val external = available.filterNot { it.packageName == packageName }
        val selected = external.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: external.firstOrNull {
                it.playbackState?.state in setOf(
                    PlaybackState.STATE_PAUSED,
                    PlaybackState.STATE_BUFFERING,
                    PlaybackState.STATE_CONNECTING,
                )
            }
            ?: external.firstOrNull { it.metadata?.description?.title != null }
        if (selected?.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = selected
            activeController?.registerCallback(controllerCallback)
        }
        ExternalSessionBridge.attach(activeController, this)
    }

    override fun onDestroy() {
        activeController?.unregisterCallback(controllerCallback)
        if (::sessionManager.isInitialized) sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
        ExternalSessionBridge.attach(null, this)
        super.onDestroy()
    }
}
