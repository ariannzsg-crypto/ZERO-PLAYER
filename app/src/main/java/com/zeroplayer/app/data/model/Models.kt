package com.zeroplayer.app.data.model

import android.net.Uri
import com.zeroplayer.app.data.db.PlaylistEntity
import com.zeroplayer.app.data.db.SongEntity

const val UNKNOWN_ARTIST = "Unknown Artist"
const val UNKNOWN_ALBUM = "Unknown Album"

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    val path: String,
    val folder: String,
    val addedAt: Long,
    val mimeType: String,
) {
    val folderName: String
        get() = folder.trimEnd('/').substringAfterLast('/').ifBlank { folder }

    val artworkUri: Uri
        get() = albumArtUri(albumId)

    val contentUri: Uri
        get() = Uri.parse("content://media/external/audio/media/$id")

    val displaySubtitle: String
        get() = if (artist.isBlank() || artist == UNKNOWN_ARTIST) album else artist
}

fun albumArtUri(albumId: Long): Uri =
    Uri.parse("content://media/external/audio/albumart/$albumId")

fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    track = track,
    year = year,
    path = path,
    folder = folder,
    addedAt = addedAt,
    mimeType = mimeType,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val durationMs: Long,
    val year: Int,
) {
    val artworkUri: Uri get() = albumArtUri(id)
}

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
)

/** A playlist row, either user made or backed by a live smart rule. */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAt: Long,
    val smartKind: SmartKind?,
    val autoArtist: String? = null,
    /** User picked cover image, stored inside the app's private files directory. */
    val coverUri: String? = null,
) {
    val isSmart: Boolean get() = smartKind != null

    /** True for playlists generated and kept in sync from the artist tags. */
    val isAuto: Boolean get() = autoArtist != null
}

enum class SmartKind(val key: String, val label: String, val description: String) {
    FAVORITES("favorites", "Favorites", "Every track you have hearted"),
    MOST_PLAYED("most_played", "Most Played", "Your heaviest rotation, by play count"),
    RECENTLY_PLAYED("recently_played", "Recently Played", "Most recently listened tracks"),
    RECENTLY_ADDED("recently_added", "Recently Added", "Newest files on the device"),
    NEVER_PLAYED("never_played", "Unplayed", "Tracks you have not listened to yet"),
    ;

    companion object {
        fun from(key: String?): SmartKind? = entries.firstOrNull { it.key == key }
        fun all() = entries.toList()
    }
}

fun PlaylistEntity.toModel(count: Int): Playlist = Playlist(
    id = id,
    name = name,
    trackCount = count,
    createdAt = createdAt,
    smartKind = SmartKind.from(smartKind),
    autoArtist = autoArtist,
    coverUri = coverUri,
)

data class SongStat(
    val songId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
    val listenedMs: Long = 0L,
)
