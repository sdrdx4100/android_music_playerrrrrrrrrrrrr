package com.sdrdx4100.quietplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.CancellationException

class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    val controller: MediaController?
        get() = if (controllerFuture.isDone && !controllerFuture.isCancelled) controllerFuture.get() else null

    suspend fun awaitController(): MediaController = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        controllerFuture.addListener({
            try {
                continuation.resume(controllerFuture.get()) { _, _, _ -> }
            } catch (error: Exception) {
                continuation.cancel(
                    CancellationException("Unable to connect to playback service").apply { initCause(error) }
                )
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun events(): Flow<Unit> = callbackFlow {
        val connected = awaitController()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { trySend(Unit) }
        }
        connected.addListener(listener)
        trySend(Unit)
        awaitClose { connected.removeListener(listener) }
    }

    fun playQueue(items: List<MediaItem>, startIndex: Int) {
        controller?.apply {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun release() = MediaController.releaseFuture(controllerFuture)
}
