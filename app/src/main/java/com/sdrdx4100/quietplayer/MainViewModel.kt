package com.sdrdx4100.quietplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.sdrdx4100.quietplayer.data.MusicRepository
import com.sdrdx4100.quietplayer.data.Song
import com.sdrdx4100.quietplayer.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val scanning: Boolean = false,
    val error: String? = null,
) {
    val currentItem: MediaItem? get() = queue.getOrNull(currentIndex)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private val connection = PlayerConnection(application)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connection.awaitController()
            connection.events().collect { syncPlayer() }
        }
        viewModelScope.launch {
            while (isActive) {
                syncPosition()
                delay(500)
            }
        }
    }

    fun scanLibrary() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, error = null) }
            runCatching { repository.loadSongs() }
                .onSuccess { songs ->
                    _state.update { it.copy(songs = songs, scanning = false) }
                    restoreQueueIfEmpty(songs)
                }
                .onFailure { error ->
                    _state.update { it.copy(scanning = false, error = error.localizedMessage ?: "Music scan failed") }
                }
        }
    }

    private suspend fun restoreQueueIfEmpty(songs: List<Song>) {
        val player = connection.awaitController()
        if (player.mediaItemCount == 0 && songs.isNotEmpty()) {
            player.setMediaItems(songs.map(Song::toMediaItem))
            player.prepare()
        }
        syncPlayer()
    }

    fun playSong(song: Song, source: List<Song> = _state.value.songs) {
        val index = source.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        viewModelScope.launch {
            connection.awaitController().apply {
                setMediaItems(source.map(Song::toMediaItem), index, 0L)
                prepare()
                play()
            }
        }
    }

    fun playQueueIndex(index: Int) = connection.controller?.apply { seekToDefaultPosition(index); play() }
    fun togglePlay() = connection.controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun previous() = connection.controller?.seekToPreviousMediaItem()
    fun next() = connection.controller?.seekToNextMediaItem()
    fun seekTo(positionMs: Long) = connection.controller?.seekTo(positionMs)

    fun toggleShuffle() = connection.controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = connection.controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeQueueItem(index: Int) {
        connection.controller?.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = connection.controller ?: return
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) {
            player.moveMediaItem(from, to)
        }
    }

    private fun syncPosition() {
        val player = connection.controller ?: return
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { value -> value != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            )
        }
    }

    private fun syncPlayer() {
        val player = connection.controller ?: return
        _state.update {
            it.copy(
                queue = List(player.mediaItemCount, player::getMediaItemAt),
                currentIndex = player.currentMediaItemIndex,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { value -> value != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }
    }

    override fun onCleared() {
        connection.release()
        super.onCleared()
    }
}
