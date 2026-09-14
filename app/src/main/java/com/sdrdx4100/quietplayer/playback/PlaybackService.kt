package com.sdrdx4100.quietplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sdrdx4100.quietplayer.MainActivity

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val launchIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        restorePositionWhenQueueArrives()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackState()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlaybackState()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun restorePositionWhenQueueArrives() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedId = prefs.getString(KEY_MEDIA_ID, null) ?: return
        val savedPosition = prefs.getLong(KEY_POSITION, 0L)
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val index = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).mediaId == savedId } ?: return
                player.seekTo(index, savedPosition)
                player.removeListener(this)
            }
        })
    }

    private fun savePlaybackState() {
        val item: MediaItem = player.currentMediaItem ?: return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_MEDIA_ID, item.mediaId)
            .putLong(KEY_POSITION, player.currentPosition)
            .apply()
    }

    companion object {
        private const val PREFS = "playback_state"
        private const val KEY_MEDIA_ID = "media_id"
        private const val KEY_POSITION = "position"
    }
}

