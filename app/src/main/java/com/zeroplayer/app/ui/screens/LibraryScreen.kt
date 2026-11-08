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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.SongActionsSheet
import com.zeroplayer.app.ui.components.SongRow
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import com.zeroplayer.app.ui.albumCountLabel
import com.zeroplayer.app.ui.songCountLabel

private val TABS = listOf("Songs", "Albums", "Artists", "Folders")

@Composable
fun LibraryScreen(library: LibraryData) {
    val nav = LocalNav.current
    var tab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(Graph.prefs.librarySort) }
    var showSortMenu by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val bottomInset = LocalBottomInset.current

    val songs = remember(library.songs, sort) { sortSongs(library.songs, sort) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displayMedium,
                        color = Zero.TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    MicroLabel(
                        text = "${library.songs.size} songs · ${library.albums.size} albums",
                    )
                }
                Box {
                    ZeroIconButton(
                        icon = Icons.Rounded.Sort,
                        contentDescription = "Sort",
                        onClick = { showSortMenu = true },
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = Zero.Surface2,
                    ) {
                        SORT_OPTIONS.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (sort == key) Zero.Chrome else Zero.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    sort = key
                                    Graph.prefs.librarySort = key
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
                ZeroIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Rescan",
                    onClick = { Graph.music.refresh() },
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TABS.forEachIndexed { index, label ->
                    LibraryTab(
                        label = label,
                        selected = tab == index,
                        onClick = { tab = index },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }

        when (tab) {
            0 -> {
                itemsIndexed(songs, key = { _, song -> "song_${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isPlaying = false,
                        isFavorite = library.isFavorite(song.id),
                        onClick = { nav.playSongs(songs, index, false) },
                        onMore = { actionSong = song },
                        leadingIndex = index,
                        showArtwork = false,
                    )
                }
                if (songs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Nothing here yet",
                            message = "No supported audio files were found in your library.",
                            actionLabel = "Rescan",
                            onAction = { Graph.music.refresh() },
                        )
                    }
                }
            }

            1 -> {
                itemsIndexed(library.albums, key = { _, album -> "album_${album.id}" }) { _, album ->
                    CollectionRow(
                        title = album.title,
                        subtitle = "${album.artist} · ${songCountLabel(album.songCount)}",
                        artworkUri = album.artworkUri.toString(),
                        onClick = { nav.push(Dest.Album(album.id, album.title)) },
                        rounded = 12.dp,
                    )
                }
            }

            2 -> {
                itemsIndexed(library.artists, key = { _, artist -> "artist_${artist.name}" }) { _, artist ->
                    val artwork = library.albumsByArtist(artist.name).firstOrNull()
                    CollectionRow(
                        title = artist.name,
                        subtitle = "${albumCountLabel(artist.albumCount)} · ${songCountLabel(artist.songCount)}",
                        artworkUri = artwork?.artworkUri?.toString(),
                        onClick = { nav.push(Dest.Artist(artist.name)) },
                        circular = true,
                    )
                }
            }

            else -> {
                itemsIndexed(library.folders, key = { _, folder -> "folder_${folder.path}" }) { _, folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { nav.push(Dest.Folder(folder.path, folder.name)) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Zero.Surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Zero.SilverDim,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Zero.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = songCountLabel(folder.songCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = Zero.TextTertiary,
                            )
                        }
                    }
                }
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
private fun LibraryTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Zero.Surface3 else Zero.Surface1.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Zero.Chrome else Zero.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rounded: androidx.compose.ui.unit.Dp = 12.dp,
    circular: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            artworkUri = artworkUri,
            label = title,
            corner = if (circular) 24.dp else rounded,
            modifier = Modifier
                .size(56.dp)
                .then(if (circular) Modifier.clip(CircleShape) else Modifier),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Zero.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun sortSongs(songs: List<Song>, sort: String): List<Song> = when (sort) {
    "ARTIST" -> songs.sortedWith(
        compareBy<Song> { it.artist.lowercase() }.thenBy { it.album.lowercase() }.thenBy { it.track },
    )
    "ALBUM" -> songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track })
    "DURATION" -> songs.sortedByDescending { it.durationMs }
    "ADDED" -> songs.sortedByDescending { it.addedAt }
    else -> songs.sortedWith(compareBy<Song> { it.title.lowercase() })
}

internal val SORT_OPTIONS = listOf(
    "TITLE" to "Title (A–Z)",
    "ARTIST" to "Artist",
    "ALBUM" to "Album",
    "DURATION" to "Longest first",
    "ADDED" to "Recently added",
)
