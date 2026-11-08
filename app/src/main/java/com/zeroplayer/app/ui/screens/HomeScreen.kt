package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Playlist
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.ArtworkCard
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.SectionHeader
import com.zeroplayer.app.ui.components.SongActionsSheet
import com.zeroplayer.app.ui.components.SongRow
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.components.ZeroDivider
import com.zeroplayer.app.ui.formatDuration
import com.zeroplayer.app.ui.formatTotalDuration
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.nav.SongListKind
import com.zeroplayer.app.ui.theme.Zero
import com.zeroplayer.app.ui.trackCountLabel

@Composable
fun HomeScreen(library: LibraryData, playback: PlaybackState) {
    val nav = LocalNav.current
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val bottomInset = LocalBottomInset.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 28.dp),
    ) {
        item {
            HomeHeader(
                onSearch = { nav.openTab(Dest.Search) },
                onSettings = { nav.push(Dest.Settings) },
            )
        }

        item {
            if (playback.hasTrack) {
                ContinueListeningCard(playback = playback, library = library)
            } else {
                LibraryHero(library = library, onPlay = {
                    if (library.songs.isNotEmpty()) nav.playSongs(library.songs, 0, false)
                }, onShuffle = {
                    if (library.songs.isNotEmpty()) nav.playSongs(library.songs, 0, true)
                })
            }
        }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickTile(
                    icon = Icons.Rounded.Favorite,
                    label = "Favorites",
                    onClick = { nav.push(Dest.SongList(SongListKind.FAVORITES, "Favorites")) },
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = Icons.Rounded.History,
                    label = "Recent",
                    onClick = { nav.push(Dest.SongList(SongListKind.RECENT, "Recently played")) },
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = Icons.Rounded.TrendingUp,
                    label = "Most played",
                    onClick = { nav.push(Dest.SongList(SongListKind.MOST_PLAYED, "Most played")) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (library.recent.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SectionHeader(
                    title = "Recently played",
                    actionLabel = "All",
                    onAction = { nav.push(Dest.SongList(SongListKind.RECENT, "Recently played")) },
                )
            }
            itemsIndexed(library.recent.take(4), key = { _, song -> "recent_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = playback.songId == song.id,
                    isFavorite = library.isFavorite(song.id),
                    onClick = { nav.playSongs(library.recent, index, false) },
                    onMore = { actionSong = song },
                )
            }
        }

        if (library.albums.isNotEmpty()) {
            item { Spacer(Modifier.height(22.dp)) }
            item {
                SectionHeader(
                    title = "Albums",
                    actionLabel = "Library",
                    onAction = { nav.openTab(Dest.Library) },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(library.albums.take(14), key = { "album_${it.id}" }) { album ->
                        ArtworkCard(
                            title = album.title,
                            subtitle = album.artist,
                            artworkUri = album.artworkUri.toString(),
                            onClick = { nav.push(Dest.Album(album.id, album.title)) },
                        )
                    }
                }
            }
        }

        if (library.playlists.isNotEmpty()) {
            item { Spacer(Modifier.height(22.dp)) }
            item {
                SectionHeader(
                    title = "Playlists",
                    actionLabel = "All",
                    onAction = { nav.openTab(Dest.Playlists) },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(library.playlists, key = { "playlist_${it.id}" }) { playlist ->
                        ArtworkCard(
                            title = playlist.name,
                            subtitle = trackCountLabel(library.playlistCount(playlist)),
                            artworkUri = library.playlistSongs(playlist).firstOrNull()?.artworkUri?.toString(),
                            onClick = { nav.push(Dest.PlaylistDetail(playlist.id, playlist.name)) },
                        )
                    }
                }
            }
        }

        if (library.mostPlayed.isNotEmpty()) {
            item { Spacer(Modifier.height(22.dp)) }
            item {
                SectionHeader(
                    title = "On repeat",
                    subtitle = "Your most played tracks",
                    actionLabel = "All",
                    onAction = { nav.push(Dest.SongList(SongListKind.MOST_PLAYED, "Most played")) },
                )
            }
            itemsIndexed(library.mostPlayed.take(4), key = { _, song -> "most_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = playback.songId == song.id,
                    isFavorite = library.isFavorite(song.id),
                    onClick = { nav.playSongs(library.mostPlayed, index, false) },
                    onMore = { actionSong = song },
                    subtitleOverride = "${song.artist} · ${library.playCount(song.id)} plays",
                )
            }
        }

        if (library.isEmpty && library.scanned) {
            item {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "No music on this device",
                    message = "Copy audio files (MP3, FLAC, WAV, AAC, OGG or M4A) to your phone, then rescan the library.",
                    actionLabel = "Rescan library",
                    onAction = { Graph.music.refresh() },
                )
            }
        }
    }

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
private fun HomeHeader(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ZERO",
                    style = MaterialTheme.typography.displayMedium.copy(brush = Zero.chromeGradient),
                    fontSize = 26.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PLAYER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Zero.TextTertiary,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            MicroLabel(text = "Offline · device library")
        }
        ZeroIconButton(icon = Icons.Rounded.Search, contentDescription = "Search", onClick = onSearch)
        ZeroIconButton(icon = Icons.Rounded.MoreHoriz, contentDescription = "Settings", onClick = onSettings)
    }
}

@Composable
private fun ContinueListeningCard(playback: PlaybackState, library: LibraryData) {
    val nav = LocalNav.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Zero.Surface2, Zero.Surface1),
                ),
            )
            .clickable { nav.openNowPlaying() }
            .padding(16.dp),
    ) {
        MicroLabel(text = "Continue listening")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                CoverArt(
                    artworkUri = playback.artworkUri,
                    label = playback.title,
                    corner = 14.dp,
                    modifier = Modifier.size(84.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playback.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Zero.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playback.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playback.progress)
                                .height(2.dp)
                                .background(Zero.silverGradient),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${formatDuration(playback.positionMs)} / ${formatDuration(playback.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Zero.TextTertiary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ZeroIconButton(
                icon = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                onClick = { PlaybackController.togglePlayPause() },
                tint = Zero.Chrome,
                iconSize = 26.dp,
            )
        }
        if (!library.isEmpty) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${library.songs.size} tracks indexed · ${library.albums.size} albums",
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
            )
        }
    }
}

@Composable
private fun LibraryHero(library: LibraryData, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Zero.Surface2, Zero.Surface1)))
            .padding(20.dp),
    ) {
        MicroLabel(text = "Your collection")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (library.isEmpty) "The library is empty" else "Everything, in one place",
            style = MaterialTheme.typography.headlineMedium,
            color = Zero.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (library.isEmpty) {
                "Grant access and ZERO PLAYER will index every track stored on this device."
            } else {
                "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutLinePlayRow(
                icon = Icons.Rounded.PlayArrow,
                label = "Play all",
                onClick = onPlay,
                filled = true,
            )
            OutLinePlayRow(
                icon = Icons.Rounded.Shuffle,
                label = "Shuffle",
                onClick = onShuffle,
            )
        }
        if (library.lastScanAt > 0L) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Library of ${formatTotalDuration(library.songs.sumOf { it.durationMs })}",
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
            )
        }
    }
}

@Composable
private fun QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Zero.Surface1)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Zero.Silver, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Zero.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
