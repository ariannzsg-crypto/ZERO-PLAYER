package com.zeroplayer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached snapshot of the device's audio library (MediaStore is the source of truth). */
@Entity(tableName = "songs", indices = [Index("albumId"), Index("artist"), Index("folder")])
data class SongEntity(
    @PrimaryKey val id: Long,
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
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long,
)

@Entity(tableName = "song_stats")
data class SongStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    /** Real accumulated listening time, sampled while the player is running. */
    val listenedMs: Long = 0L,
)

@Entity(tableName = "playlists", indices = [Index("autoArtist")])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    /** Non-null for smart playlists; see [com.zeroplayer.app.data.model.SmartKind]. */
    val smartKind: String? = null,
    /** Non-null when the playlist is generated for this artist and kept in sync. */
    val autoArtist: String? = null,
    /** App-private `file://` URI of a user picked cover, null for the artwork mosaic. */
    val coverUri: String? = null,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "eq_profiles")
data class EqProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Comma separated band gains in dB, low band first. */
    val levels: String,
    val preampDb: Float,
    val bassBoost: Int,
    val virtualizer: Int,
    val builtin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val raw: String,
    val synced: Boolean,
    val source: String,
    val importedAt: Long,
)
