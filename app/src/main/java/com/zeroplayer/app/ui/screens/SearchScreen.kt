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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.SongActionsSheet
import com.zeroplayer.app.ui.components.SongRow
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero

@Composable
fun SearchScreen(library: LibraryData) {
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var recentSearches by remember { mutableStateOf(Graph.prefs.recentSearches) }
    val bottomInset = LocalBottomInset.current

    val results = remember(library.songs, query) { library.search(query) }
    val albums = remember(library.albums, query) {
        if (query.isBlank()) emptyList() else library.albums.filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        }
    }
    val artists = remember(library.artists, query) {
        if (query.isBlank()) emptyList() else library.artists.filter { it.name.contains(query, true) }
    }

    fun commitSearch(text: String) {
        if (text.isBlank()) return
        val updated = (listOf(text) + recentSearches.filter { !it.equals(text, true) }).take(8)
        recentSearches = updated
        Graph.prefs.recentSearches = updated
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text("Search", style = MaterialTheme.typography.displayMedium, color = Zero.TextPrimary)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Zero.Surface1)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Zero.TextTertiary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Songs, albums, artists, folders",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Zero.TextTertiary,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Zero.TextPrimary,
                                fontSize = 15.sp,
                            ),
                            cursorBrush = SolidColor(Zero.Chrome),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Zero.TextTertiary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    commitSearch(query)
                                    query = ""
                                },
                        )
                    }
                }
            }
        }

        if (query.isBlank()) {
            if (recentSearches.isNotEmpty()) {
                item { MicroLabel("Recent searches", modifier = Modifier.padding(start = 22.dp, bottom = 8.dp)) }
                items(recentSearches) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { query = term }
                            .padding(horizontal = 22.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Zero.TextTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(term, style = MaterialTheme.typography.bodyLarge, color = Zero.TextSecondary)
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                MicroLabel("Browse", modifier = Modifier.padding(start = 22.dp, bottom = 8.dp))
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrowseChip("All songs", Modifier.weight(1f)) {
                        nav.push(Dest.SongList(com.zeroplayer.app.ui.nav.SongListKind.ALL, "All songs"))
                    }
                    BrowseChip("Favorites", Modifier.weight(1f)) {
                        nav.push(Dest.SongList(com.zeroplayer.app.ui.nav.SongListKind.FAVORITES, "Favorites"))
                    }
                    BrowseChip("Recent", Modifier.weight(1f)) {
                        nav.push(Dest.SongList(com.zeroplayer.app.ui.nav.SongListKind.RECENT, "Recently played"))
                    }
                }
            }
        } else {
            if (results.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "No matches",
                        message = "Nothing in your library matches “$query”.",
                    )
                }
            }
            if (artists.isNotEmpty()) {
                item { MicroLabel("Artists", modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 6.dp)) }
                items(artists.take(5), key = { "artist_${it.name}" }) { artist ->
                    CollectionRow(
                        title = artist.name,
                        subtitle = "${artist.songCount} songs",
                        artworkUri = library.albumsByArtist(artist.name).firstOrNull()?.artworkUri?.toString(),
                        onClick = { nav.push(Dest.Artist(artist.name)) },
                        circular = true,
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item { MicroLabel("Albums", modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 6.dp)) }
                items(albums.take(5), key = { "album_${it.id}" }) { album ->
                    CollectionRow(
                        title = album.title,
                        subtitle = album.artist,
                        artworkUri = album.artworkUri.toString(),
                        onClick = { nav.push(Dest.Album(album.id, album.title)) },
                    )
                }
            }
            if (results.isNotEmpty()) {
                item { MicroLabel("Songs", modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 6.dp)) }
                itemsIndexed(results, key = { _, song -> "search_${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isPlaying = false,
                        isFavorite = library.isFavorite(song.id),
                        onClick = { nav.playSongs(results, index, false) },
                        onMore = { actionSong = song },
                        subtitleOverride = "${song.artist} · ${song.album}",
                    )
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
private fun BrowseChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Zero.Surface1)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Zero.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
