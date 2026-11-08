package com.zeroplayer.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zeroplayer.app.data.model.Song

const val EXTRA_ALBUM_ID = "zero.albumId"
const val EXTRA_SONG_ID = "zero.songId"

/** Builds a Media3 item carrying everything the notification and queue need. */
fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(android.os.Bundle().apply {
            putLong(EXTRA_SONG_ID, id)
            putLong(EXTRA_ALBUM_ID, albumId)
        })
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

fun MediaItem.songId(): Long =
    mediaId.toLongOrNull()
        ?: mediaMetadata.extras?.getLong(EXTRA_SONG_ID)
        ?: -1L

fun MediaItem.artworkUriOrNull(): Uri? = mediaMetadata.artworkUri

/** Lightweight view of a queue entry rendered by the queue sheet. */
data class QueueEntry(
    val songId: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val durationMs: Long,
)

fun MediaItem.toQueueEntry(durationMs: Long): QueueEntry = QueueEntry(
    songId = songId(),
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
    durationMs = durationMs,
)
