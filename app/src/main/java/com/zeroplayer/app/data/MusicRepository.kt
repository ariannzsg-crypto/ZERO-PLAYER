package com.zeroplayer.app.data

import android.content.Context
import com.zeroplayer.app.data.db.AppDatabase
import com.zeroplayer.app.data.db.EqProfileEntity
import com.zeroplayer.app.data.db.FavoriteEntity
import com.zeroplayer.app.data.db.PlaylistCount
import com.zeroplayer.app.data.db.PlaylistEntity
import com.zeroplayer.app.data.db.PlaylistTrackEntity
import com.zeroplayer.app.data.db.SongEntity
import com.zeroplayer.app.data.db.SongStatEntity
import com.zeroplayer.app.data.model.Album
import com.zeroplayer.app.data.model.Artist
import com.zeroplayer.app.data.model.Folder
import com.zeroplayer.app.data.model.Playlist
import com.zeroplayer.app.data.model.SmartKind
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.data.model.SongStat
import com.zeroplayer.app.data.model.toModel
import com.zeroplayer.app.data.model.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the UI needs to render the library, derived from persistent state. */
data class LibraryData(
    val songs: List<Song> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val stats: Map<Long, SongStat> = emptyMap(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val recent: List<Song> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistSongIds: Map<Long, List<Long>> = emptyMap(),
    val eqProfiles: List<EqProfileEntity> = emptyList(),
    val scanned: Boolean = false,
    val scanning: Boolean = false,
    val lastScanAt: Long = 0L,
) {
    val isEmpty: Boolean get() = songs.isEmpty()

    fun isFavorite(songId: Long): Boolean = songId in favoriteIds

    fun playCount(songId: Long): Int = stats[songId]?.playCount ?: 0

    fun songsFor(albumId: Long): List<Song> =
        songs.filter { it.albumId == albumId }.sortedBy { it.track }

    fun songsByArtist(artist: String): List<Song> =
        songs.filter { it.artist == artist }.sortedWith(compareBy({ it.album }, { it.track }))

    fun songsInFolder(path: String): List<Song> =
        songs.filter { it.folder == path }.sortedBy { it.title.lowercase() }

    fun albumsByArtist(artist: String): List<Album> = albums.filter { it.artist == artist }

    /** Resolves any playlist (manual or smart) to a playable, ordered track list. */
    fun playlistSongs(playlist: Playlist): List<Song> {
        val smart = playlist.smartKind
        if (smart != null) {
            return when (smart) {
                SmartKind.FAVORITES -> favoriteSongs
                SmartKind.MOST_PLAYED -> mostPlayed
                SmartKind.RECENTLY_PLAYED -> recent
                SmartKind.RECENTLY_ADDED -> songs.sortedByDescending { it.addedAt }
                SmartKind.NEVER_PLAYED -> songs.filter { playCount(it.id) == 0 }
                    .sortedBy { it.title.lowercase() }
            }
        }
        val ids = playlistSongIds[playlist.id].orEmpty()
        if (ids.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun playlistCount(playlist: Playlist): Int =
        if (playlist.smartKind == null) playlist.trackCount else playlistSongs(playlist).size

    fun search(query: String): List<Song> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return songs.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folderName.lowercase().contains(q)
        }
    }
}

class MusicRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanner = MediaStoreScanner(context)

    private val scanning = MutableStateFlow(false)
    private val scanned = MutableStateFlow(false)

    private data class Meta(
        val playlists: List<PlaylistEntity>,
        val counts: Map<Long, Int>,
        val playlistsSongs: Map<Long, List<Long>>,
        val eqProfiles: List<EqProfileEntity>,
        val scanning: Boolean,
        val scanned: Boolean,
    )

    private val metaFlow = combine(
        db.playlists().observePlaylists(),
        db.playlists().observeCounts(),
        db.playlists().observeAllTracks(),
        db.eqProfiles().observeAll(),
        combine(scanning, scanned) { a, b -> a to b },
    ) { playlists: List<PlaylistEntity>, counts: List<PlaylistCount>, tracks: List<PlaylistTrackEntity>,
        eq: List<EqProfileEntity>, scan: Pair<Boolean, Boolean> ->
        Meta(
            playlists = playlists,
            counts = counts.associate { it.playlistId to it.count },
            playlistsSongs = tracks.groupBy { it.playlistId }
                .mapValues { entry -> entry.value.sortedBy { it.position }.map { it.songId } },
            eqProfiles = eq,
            scanning = scan.first,
            scanned = scan.second,
        )
    }

    val state: StateFlow<LibraryData> = combine(
        db.songs().observeAll(),
        db.favorites().observeIds(),
        db.stats().observeAll(),
        metaFlow,
    ) { songRows: List<SongEntity>, favIds: List<Long>, statRows: List<SongStatEntity>, meta: Meta ->
        buildLibrary(songRows, favIds, statRows, meta)
    }.flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LibraryData())

    private fun buildLibrary(
        songRows: List<SongEntity>,
        favIds: List<Long>,
        statRows: List<SongStatEntity>,
        meta: Meta,
    ): LibraryData {
        val songs = songRows.map(SongEntity::toSong)
        val stats = statRows.associate { it.songId to SongStat(it.songId, it.playCount, it.lastPlayedAt) }
        val favorites = favIds.toSet()
        val byId = songs.associateBy { it.id }

        val albums = songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                Album(
                    id = albumId,
                    title = list.first().album,
                    artist = list.first().artist,
                    songCount = list.size,
                    durationMs = list.sumOf { it.durationMs },
                    year = list.maxOfOrNull { it.year } ?: 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val artists = songs.groupBy { it.artist }
            .map { (name, list) ->
                Artist(name = name, songCount = list.size, albumCount = list.map { it.albumId }.distinct().size)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val folders = songs.groupBy { it.folder }
            .map { (path, list) -> Folder(path = path, name = list.first().folderName, songCount = list.size) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val recent = stats.values
            .filter { it.lastPlayedAt > 0 }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { byId[it.songId] }
            .take(100)

        val mostPlayed = stats.values
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<SongStat> { it.playCount }.thenByDescending { it.lastPlayedAt })
            .mapNotNull { byId[it.songId] }
            .take(100)

        return LibraryData(
            songs = songs,
            favoriteIds = favorites,
            stats = stats,
            albums = albums,
            artists = artists,
            folders = folders,
            recent = recent,
            mostPlayed = mostPlayed,
            favoriteSongs = songs.filter { it.id in favorites }.sortedBy { it.title.lowercase() },
            playlists = meta.playlists.map { it.toModel(meta.counts[it.id] ?: 0) },
            playlistSongIds = meta.playlistsSongs,
            eqProfiles = meta.eqProfiles,
            scanned = meta.scanned,
            scanning = meta.scanning,
            lastScanAt = prefs.lastScanAt,
        )
    }

    fun refresh() {
        if (scanning.value) return
        scope.launch { runScan() }
    }

    private suspend fun runScan() {
        scanning.value = true
        try {
            val found = scanner.scan()
            db.songs().replaceAll(found)
            db.favorites().prune()
            db.stats().prune()
            db.playlists().pruneTracks()
            prefs.lastScanAt = System.currentTimeMillis()
        } catch (_: SecurityException) {
            // Permission revoked mid-scan; keep the previous cached library.
        } finally {
            scanning.value = false
            scanned.value = true
        }
    }

    suspend fun songsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val rows = db.songs().byIds(ids).associateBy { it.id }
        ids.mapNotNull { rows[it]?.toSong() }
    }

    fun observePlaylistSongs(playlistId: Long): StateFlow<List<Song>> {
        val flow = combine(
            db.playlists().observeTracks(playlistId),
            db.songs().observeAll(),
        ) { tracks: List<PlaylistTrackEntity>, songs: List<SongEntity> ->
            val byId = songs.associateBy { it.id }
            tracks.mapNotNull { byId[it.songId]?.toSong() }
        }
        return flow.stateIn(scope, SharingStarted.WhileSubscribed(2_000), emptyList())
    }

    suspend fun playlistSongs(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        val ids = db.playlists().tracks(playlistId).map { it.songId }
        if (ids.isEmpty()) return@withContext emptyList()
        val rows = db.songs().byIds(ids).associateBy { it.id }
        ids.mapNotNull { rows[it]?.toSong() }
    }

    suspend fun toggleFavorite(songId: Long): Boolean = withContext(Dispatchers.IO) {
        val isFav = db.favorites().isFavorite(songId)
        if (isFav) db.favorites().remove(songId) else db.favorites().add(FavoriteEntity(songId, System.currentTimeMillis()))
        !isFav
    }

    suspend fun recordPlay(songId: Long) = withContext(Dispatchers.IO) { db.stats().recordPlay(songId) }

    suspend fun createPlaylist(name: String, smartKind: SmartKind? = null): Long = withContext(Dispatchers.IO) {
        db.playlists().insert(
            PlaylistEntity(
                name = name.trim().ifBlank { "Untitled" },
                createdAt = System.currentTimeMillis(),
                smartKind = smartKind?.key,
            ),
        )
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        db.playlists().rename(id, name.trim().ifBlank { "Untitled" })
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.playlists().clearTracks(id)
        db.playlists().deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        var position = db.playlists().maxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        songIds.forEach { songId ->
            db.playlists().insertTrack(PlaylistTrackEntity(playlistId, songId, position++, now))
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.playlists().removeTrack(playlistId, songId)
        val remaining = db.playlists().tracks(playlistId).map { it.songId }
        db.playlists().reorder(playlistId, remaining)
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>) = withContext(Dispatchers.IO) {
        db.playlists().reorder(playlistId, orderedSongIds)
    }

    suspend fun allEqProfiles(): List<EqProfileEntity> = withContext(Dispatchers.IO) { db.eqProfiles().all() }

    suspend fun saveEqProfile(profile: EqProfileEntity): Long =
        withContext(Dispatchers.IO) { db.eqProfiles().insert(profile) }

    suspend fun deleteEqProfile(id: Long) = withContext(Dispatchers.IO) { db.eqProfiles().delete(id) }

    suspend fun ensureDefaultEqProfiles(seed: List<EqProfileEntity>) = withContext(Dispatchers.IO) {
        if (db.eqProfiles().count() == 0) seed.forEach { db.eqProfiles().insert(it) }
    }
}
