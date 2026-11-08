package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.albumCountLabel
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.SongActionsSheet
import com.zeroplayer.app.ui.components.SongRow
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.formatTotalDuration
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.nav.SongListKind
import com.zeroplayer.app.ui.songCountLabel
import com.zeroplayer.app.ui.theme.Zero

@Composable
fun AlbumScreen(albumId: Long, fallbackTitle: String, library: LibraryData, playback: PlaybackState) {
    val nav = LocalNav.current
    val album = library.albums.firstOrNull { it.id == albumId }
    val songs = remember(library.songs, albumId) { library.songsFor(albumId) }
    var actionSong by remember { mutableStateOf<Song?>(null) }

    TrackListScreen(
        title = album?.title ?: fallbackTitle,
        subtitle = album?.let { "${it.artist} · ${it.year.takeIf { year -> year > 0 } ?: ""}".trim(' ', '·') } ?: "",
        meta = album?.let { "${songCountLabel(it.songCount)} · ${formatTotalDuration(it.durationMs)}" },
        artworkUri = album?.artworkUri?.toString(),
        songs = songs,
        playback = playback,
        library = library,
        onAction = { actionSong = it },
    )

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            library = library,
            onDismiss = { actionSong = null },
            onGoToArtist = { nav.push(Dest.Artist(song.artist)) },
        )
    }
}

@Composable
fun ArtistScreen(artistName: String, library: LibraryData, playback: PlaybackState) {
    val nav = LocalNav.current
    val songs = remember(library.songs, artistName) { library.songsByArtist(artistName) }
    val albums = remember(library.albums, artistName) { library.albumsByArtist(artistName) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val bottomInset = LocalBottomInset.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item {
            DetailTopBar(onBack = { nav.pop() })
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoverArt(
                    artworkUri = albums.firstOrNull()?.artworkUri?.toString(),
                    label = artistName,
                    corner = 42.dp,
                    modifier = Modifier.size(132.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Zero.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${albumCountLabel(albums.size)} · ${songCountLabel(songs.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutLinePlayRow(Icons.Rounded.PlayArrow, "Play", { nav.playSongs(songs, 0, false) }, filled = true)
                    OutLinePlayRow(Icons.Rounded.Shuffle, "Shuffle", { nav.playSongs(songs, 0, true) })
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (albums.isNotEmpty()) {
            item { MicroLabel("Albums", modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 6.dp)) }
            itemsIndexed(albums, key = { _, album -> "artist_album_${album.id}" }) { _, album ->
                CollectionRow(
                    title = album.title,
                    subtitle = "${album.year.takeIf { it > 0 }?.toString() ?: "—"} · ${songCountLabel(album.songCount)}",
                    artworkUri = album.artworkUri.toString(),
                    onClick = { nav.push(Dest.Album(album.id, album.title)) },
                )
            }
        }
        item { MicroLabel("Songs", modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp)) }
        itemsIndexed(songs, key = { _, song -> "artist_song_${song.id}" }) { index, song ->
            SongRow(
                song = song,
                isPlaying = playback.songId == song.id,
                isFavorite = library.isFavorite(song.id),
                onClick = { nav.playSongs(songs, index, false) },
                onMore = { actionSong = song },
            )
        }
    }

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            library = library,
            onDismiss = { actionSong = null },
            onGoToAlbum = { nav.push(Dest.Album(song.albumId, song.album)) },
        )
    }
}

@Composable
fun FolderScreen(path: String, fallbackTitle: String, library: LibraryData, playback: PlaybackState) {
    val songs = remember(library.songs, path) { library.songsInFolder(path) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val nav = LocalNav.current

    TrackListScreen(
        title = songs.firstOrNull()?.folderName ?: fallbackTitle,
        subtitle = path,
        meta = songCountLabel(songs.size),
        artworkUri = null,
        songs = songs,
        playback = playback,
        library = library,
        onAction = { actionSong = it },
        headerIcon = Icons.Rounded.Folder,
    )

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            library = library,
            onDismiss = { actionSong = null },
            onGoToAlbum = { nav.push(Dest.Album(song.albumId, song.album)) },
            onGoToArtist = { nav.push(Dest.Artist(song.artist)) },
        )
    }
}

@Composable
fun SongListScreen(kind: SongListKind, title: String, library: LibraryData, playback: PlaybackState) {
    val nav = LocalNav.current
    val songs = remember(library.songs, library.favoriteIds, library.recent, library.mostPlayed, kind) {
        when (kind) {
            SongListKind.ALL -> library.songs.sortedBy { it.title.lowercase() }
            SongListKind.FAVORITES -> library.favoriteSongs
            SongListKind.RECENT -> library.recent
            SongListKind.MOST_PLAYED -> library.mostPlayed
        }
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }

    TrackListScreen(
        title = title,
        subtitle = null,
        meta = songCountLabel(songs.size),
        artworkUri = songs.firstOrNull()?.artworkUri?.toString(),
        songs = songs,
        playback = playback,
        library = library,
        onAction = { actionSong = it },
        emptyIcon = Icons.Rounded.Favorite,
        emptyMessage = when (kind) {
            SongListKind.FAVORITES -> "Tap the heart on any track to keep it here."
            SongListKind.RECENT -> "Tracks you listen to will show up here."
            SongListKind.MOST_PLAYED -> "Play something and your rotation will build up here."
            SongListKind.ALL -> "No tracks indexed yet."
        },
    )

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            library = library,
            onDismiss = { actionSong = null },
            onGoToAlbum = { nav.push(Dest.Album(song.albumId, song.album)) },
            onGoToArtist = { nav.push(Dest.Artist(song.artist)) },
        )
    }
}

@Composable
private fun TrackListScreen(
    title: String,
    subtitle: String?,
    meta: String?,
    artworkUri: String?,
    songs: List<Song>,
    playback: PlaybackState,
    library: LibraryData,
    onAction: (Song) -> Unit,
    headerIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.PlayArrow,
    emptyMessage: String = "No tracks found in this collection.",
) {
    val nav = LocalNav.current
    val bottomInset = LocalBottomInset.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item { DetailTopBar(onBack = { nav.pop() }) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (headerIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Zero.Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(headerIcon, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(32.dp))
                    }
                } else {
                    CoverArt(
                        artworkUri = artworkUri,
                        label = title,
                        corner = 18.dp,
                        modifier = Modifier.size(96.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Zero.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Zero.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!meta.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = Zero.TextTertiary,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutLinePlayRow(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play",
                    onClick = { if (songs.isNotEmpty()) nav.playSongs(songs, 0, false) },
                    filled = true,
                )
                OutLinePlayRow(
                    icon = Icons.Rounded.Shuffle,
                    label = "Shuffle",
                    onClick = { if (songs.isNotEmpty()) nav.playSongs(songs, 0, true) },
                )
            }
        }

        if (songs.isEmpty()) {
            item {
                EmptyState(
                    icon = emptyIcon,
                    title = "Empty",
                    message = emptyMessage,
                )
            }
        }

        itemsIndexed(songs, key = { _, song -> "track_${song.id}" }) { index, song ->
            SongRow(
                song = song,
                isPlaying = playback.songId == song.id,
                isFavorite = library.isFavorite(song.id),
                onClick = { nav.playSongs(songs, index, false) },
                onMore = { onAction(song) },
                leadingIndex = index,
                showArtwork = false,
            )
        }
    }
}

@Composable
fun DetailTopBar(onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZeroIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            tint = Zero.Silver,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Suppress("unused")
private val detailHeaderGradient = Brush.verticalGradient(listOf(Zero.Surface1, Zero.Black))

@Suppress("unused")
private val detailCircle = CircleShape
