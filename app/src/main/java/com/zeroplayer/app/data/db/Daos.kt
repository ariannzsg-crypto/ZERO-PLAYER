package com.zeroplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE id NOT IN (:keep)")
    suspend fun deleteExcept(keep: List<Long>)

    @Transaction
    suspend fun insertMissing(items: List<SongEntity>): Int {
        if (items.isEmpty()) return 0
        upsertAll(items)
        return items.size
    }

    @Transaction
    suspend fun replaceAll(items: List<SongEntity>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT songId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("DELETE FROM favorites WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun prune()
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM song_stats")
    fun observeAll(): Flow<List<SongStatEntity>>

    @Query("SELECT * FROM song_stats WHERE songId = :songId")
    suspend fun get(songId: Long): SongStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SongStatEntity)

    @Transaction
    suspend fun recordPlay(songId: Long, at: Long = System.currentTimeMillis()) {
        val existing = get(songId) ?: SongStatEntity(songId)
        upsert(existing.copy(playCount = existing.playCount + 1, lastPlayedAt = at))
    }

    @Query("DELETE FROM song_stats WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun prune()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_tracks GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistCount>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observeAllTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun tracks(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearTracks(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeTrack(playlistId: Long, songId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("DELETE FROM playlist_tracks WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun pruneTracks()

    @Transaction
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        val existing = tracks(playlistId).associateBy { it.songId }
        val now = System.currentTimeMillis()
        clearTracks(playlistId)
        insertTracks(
            orderedSongIds.mapIndexedNotNull { index, songId ->
                existing[songId]?.copy(position = index)
                    ?: PlaylistTrackEntity(playlistId, songId, index, now)
            },
        )
    }
}

data class PlaylistCount(val playlistId: Long, val count: Int)

@Dao
interface EqProfileDao {
    @Query("SELECT * FROM eq_profiles ORDER BY builtin DESC, createdAt ASC")
    fun observeAll(): Flow<List<EqProfileEntity>>

    @Query("SELECT * FROM eq_profiles ORDER BY builtin DESC, createdAt ASC")
    suspend fun all(): List<EqProfileEntity>

    @Query("SELECT * FROM eq_profiles WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): EqProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: EqProfileEntity): Long

    @Query("DELETE FROM eq_profiles WHERE id = :id AND builtin = 0")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM eq_profiles")
    suspend fun count(): Int
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun forSong(songId: Long): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun observe(songId: Long): Flow<LyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun delete(songId: Long)
}
