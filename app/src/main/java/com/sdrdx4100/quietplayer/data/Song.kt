package com.sdrdx4100.quietplayer.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
) {
    val artworkUri: Uri = Uri.parse("content://media/external/audio/albumart/$albumId")

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setExtras(Bundle().apply { putLong(DURATION_KEY, durationMs) })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
}

const val DURATION_KEY = "duration_ms"

internal fun String?.musicLabel(fallback: String): String =
    this?.takeUnless { it.isBlank() || it == "<unknown>" } ?: fallback
