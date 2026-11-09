package com.zeroplayer.app.data

import androidx.room.withTransaction
import com.zeroplayer.app.data.db.AppDatabase
import com.zeroplayer.app.data.db.PlaylistEntity
import com.zeroplayer.app.data.db.PlaylistTrackEntity
import com.zeroplayer.app.data.db.SongEntity
import com.zeroplayer.app.data.model.UNKNOWN_ARTIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of one artist-playlist pass, used for the message in Settings → AI. */
data class ArtistSyncResult(
    val created: Int = 0,
    val refreshed: Int = 0,
    val removed: Int = 0,
    val artists: Int = 0,
) {
    val changed: Boolean get() = created + refreshed + removed > 0

    fun describe(): String = when {
        artists == 0 -> "No tracks with a known artist yet."
        !changed -> "$artists artist playlists were already up to date."
        else -> "$artists artist playlists · $created new, $refreshed refreshed, $removed removed"
    }
}

/**
 * Keeps one playlist per artist in sync with the library.
 *
 * Tracking is stored in `playlists.autoArtist`, so renaming or reordering a
 * generated playlist is preserved: the pass only rewrites membership, never the
 * name. Playlists whose artist disappeared from the device are pruned.
 */
class ArtistPlaylistSync(private val db: AppDatabase) {

    suspend fun sync(): ArtistSyncResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val songs = db.songs().allRows()
            val grouped = songs
                .map { it.artist.trim() }
                .zip(songs)
                .filter { (artist, _) -> artist.isNotBlank() && artist != UNKNOWN_ARTIST }
                .groupBy({ it.first }, { it.second })

            val existing = db.playlists().autoPlaylists().associateBy { it.autoArtist }
            val now = System.currentTimeMillis()
            var created = 0
            var refreshed = 0
            var removed = 0

            grouped.forEach { (artist, tracks) ->
                val desired = tracks
                    .sortedWith(
                        compareBy<SongEntity>(
                            { it.album.lowercase() },
                            { it.track },
                            { it.title.lowercase() },
                        ),
                    )
                    .map { it.id }
                val current = existing[artist]
                val playlistId = if (current == null) {
                    created++
                    db.playlists().insert(
                        PlaylistEntity(
                            name = artist,
                            createdAt = now,
                            autoArtist = artist,
                        ),
                    )
                } else {
                    val currentIds = db.playlists().tracks(current.id).map { it.songId }
                    if (currentIds == desired) return@forEach
                    refreshed++
                    current.id
                }
                db.playlists().clearTracks(playlistId)
                if (desired.isNotEmpty()) {
                    db.playlists().insertTracks(
                        desired.mapIndexed { index, songId ->
                            PlaylistTrackEntity(playlistId, songId, index, now)
                        },
                    )
                }
            }

            existing.forEach { (artist, playlist) ->
                if (artist != null && artist !in grouped) {
                    db.playlists().clearTracks(playlist.id)
                    db.playlists().deletePlaylist(playlist.id)
                    removed++
                }
            }

            ArtistSyncResult(
                created = created,
                refreshed = refreshed,
                removed = removed,
                artists = grouped.size,
            )
        }
    }
}
