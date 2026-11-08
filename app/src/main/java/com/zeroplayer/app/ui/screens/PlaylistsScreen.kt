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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Playlist
import com.zeroplayer.app.data.model.SmartKind
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.SectionHeader
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import com.zeroplayer.app.ui.trackCountLabel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PlaylistsScreen(library: LibraryData) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val bottomInset = LocalBottomInset.current

    val smartPlaylists = remember(library.playlists) { library.playlists.filter { it.isSmart } }
    val userPlaylists = remember(library.playlists) { library.playlists.filter { !it.isSmart } }

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
                    .padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Playlists", style = MaterialTheme.typography.displayMedium, color = Zero.TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    MicroLabel("${userPlaylists.size} yours · ${smartPlaylists.size} smart")
                }
                OutLinePlayRow(
                    icon = Icons.Rounded.Add,
                    label = "New",
                    onClick = {
                        newName = ""
                        showCreate = true
                    },
                )
            }
        }

        if (smartPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "Smart playlists", subtitle = "Live rules, always up to date")
            }
            items(smartPlaylists, key = { "smart_${it.id}" }) { playlist ->
                SmartPlaylistRow(playlist = playlist, library = library) {
                    nav.push(Dest.PlaylistDetail(playlist.id, playlist.name))
                }
            }
        }

        if (userPlaylists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                SectionHeader(title = "Your playlists")
            }
            items(userPlaylists, key = { "user_${it.id}" }) { playlist ->
                UserPlaylistRow(playlist = playlist, library = library) {
                    nav.push(Dest.PlaylistDetail(playlist.id, playlist.name))
                }
            }
        }

        if (userPlaylists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Zero.Surface1)
                        .padding(22.dp),
                ) {
                    Text(
                        "Build your first playlist",
                        style = MaterialTheme.typography.titleLarge,
                        color = Zero.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Group tracks by mood, tempo or era. Reorder them with a long press.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Zero.TextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutLinePlayRow(
                        icon = Icons.Rounded.Add,
                        label = "Create playlist",
                        onClick = { showCreate = true },
                        filled = true,
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Zero.Surface1,
            title = { Text("New playlist", color = Zero.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name", color = Zero.TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Zero.Silver,
                        unfocusedBorderColor = Zero.Hairline,
                        cursorColor = Zero.Chrome,
                        focusedTextColor = Zero.TextPrimary,
                        unfocusedTextColor = Zero.TextPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { Graph.music.createPlaylist(newName.ifBlank { "New playlist" }) }
                    showCreate = false
                }) { Text("Create", color = Zero.Chrome) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel", color = Zero.TextTertiary) }
            },
        )
    }
}

@Composable
private fun SmartPlaylistRow(playlist: Playlist, library: LibraryData, onClick: () -> Unit) {
    val kind = playlist.smartKind ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Zero.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kind.icon(), contentDescription = null, tint = Zero.Silver, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = Zero.TextPrimary)
            Text(
                text = "${kind.description} · ${trackCountLabel(library.playlistCount(playlist))}",
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserPlaylistRow(playlist: Playlist, library: LibraryData, onClick: () -> Unit) {
    val songs = remember(library.songs, library.playlistSongIds, playlist.id) { library.playlistSongs(playlist) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Zero.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(20.dp))
            }
        } else {
            CoverArt(
                artworkUri = songs.first().artworkUri.toString(),
                label = playlist.name,
                corner = 14.dp,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Zero.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trackCountLabel(songs.size),
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
            )
        }
    }
}

fun SmartKind.icon(): ImageVector = when (this) {
    SmartKind.FAVORITES -> Icons.Rounded.Favorite
    SmartKind.MOST_PLAYED -> Icons.Rounded.TrendingUp
    SmartKind.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartKind.RECENTLY_ADDED -> Icons.Rounded.NewReleases
    SmartKind.NEVER_PLAYED -> Icons.Rounded.AutoAwesome
}

@Suppress("unused")
private val playlistRowArrangement = Arrangement.Center
