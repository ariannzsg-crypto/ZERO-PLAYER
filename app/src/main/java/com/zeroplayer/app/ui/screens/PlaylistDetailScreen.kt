package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.SongActionsSheet
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import com.zeroplayer.app.ui.trackCountLabel
import kotlinx.coroutines.launch

private val RowHeight = 62.dp

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    fallbackTitle: String,
    library: LibraryData,
    playback: PlaybackState,
) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val playlist = remember(library.playlists, playlistId) { library.playlists.firstOrNull { it.id == playlistId } }
    val isSmart = playlist?.isSmart == true
    val resolved = remember(playlist, library.songs, library.playlistSongIds, library.favoriteIds) {
        playlist?.let { library.playlistSongs(it) } ?: emptyList()
    }

    var order by remember(playlistId) { mutableStateOf(resolved) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(resolved) {
        if (!dragging) order = resolved
    }

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val bottomInset = LocalBottomInset.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item {
            DetailTopBar(
                onBack = { nav.pop() },
                trailing = {
                    if (!isSmart) {
                        Box {
                            ZeroIconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                onClick = { showMenu = true },
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = Zero.Surface2,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = Zero.TextSecondary) },
                                    onClick = {
                                        renameValue = playlist?.name.orEmpty()
                                        showMenu = false
                                        showRename = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Zero.TextTertiary)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete playlist", color = Zero.Danger) },
                                    onClick = {
                                        showMenu = false
                                        showDelete = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Zero.Danger)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                if (resolved.isNotEmpty()) {
                    CoverArt(
                        artworkUri = resolved.first().artworkUri.toString(),
                        label = fallbackTitle,
                        corner = 18.dp,
                        modifier = Modifier.size(110.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    text = playlist?.name ?: fallbackTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Zero.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = trackCountLabel(resolved.size) + if (isSmart) " · smart" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutLinePlayRow(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Play",
                        onClick = { if (order.isNotEmpty()) nav.playSongs(order, 0, false) },
                        filled = true,
                    )
                    OutLinePlayRow(
                        icon = Icons.Rounded.Shuffle,
                        label = "Shuffle",
                        onClick = { if (order.isNotEmpty()) nav.playSongs(order, 0, true) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!isSmart && order.isNotEmpty()) {
                    MicroLabel("Long press a track to reorder")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (order.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Nothing here yet",
                    message = if (isSmart) {
                        "This smart playlist updates itself as you listen."
                    } else {
                        "Add songs from any track menu to build this playlist."
                    },
                )
            }
        }

        items(order.size, key = { index -> "pl_${order[index].id}" }) { index ->
            val song = order[index]
            val isDragging = index == dragIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .background(if (isDragging) Zero.Surface2 else androidx.compose.ui.graphics.Color.Transparent)
                    .then(
                        if (!isSmart) {
                            Modifier.pointerInput(order.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragIndex = index
                                        dragOffset = 0f
                                        dragging = true
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val rowPx = RowHeight.toPx()
                                        val shift = (dragOffset / rowPx).toInt()
                                        if (shift != 0) {
                                            val target = (dragIndex + shift).coerceIn(0, order.lastIndex)
                                            if (target != dragIndex) {
                                                val items = order.toMutableList()
                                                val moved = items.removeAt(dragIndex)
                                                items.add(target, moved)
                                                order = items
                                                dragIndex = target
                                                dragOffset -= shift * rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        dragIndex = -1
                                        dragOffset = 0f
                                        scope.launch {
                                            Graph.music.reorderPlaylist(playlistId, order.map { it.id })
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        dragIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .height(RowHeight)
                    .clickable {
                        val playIndex = order.indexOfFirst { it.id == song.id }
                        if (playIndex >= 0) nav.playSongs(order, playIndex, false)
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isSmart) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Reorder",
                        tint = Zero.TextTertiary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                CoverArt(
                    artworkUri = song.artworkUri.toString(),
                    label = song.title,
                    corner = 8.dp,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (playback.songId == song.id) Zero.Chrome else Zero.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Zero.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isSmart) {
                    ZeroIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove",
                        tint = Zero.TextTertiary,
                        size = 34.dp,
                        iconSize = 17.dp,
                        onClick = {
                            scope.launch { Graph.music.removeFromPlaylist(playlistId, song.id) }
                        },
                    )
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Zero.Surface1,
            title = { Text("Rename playlist", color = Zero.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
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
                    scope.launch { Graph.music.renamePlaylist(playlistId, renameValue) }
                    showRename = false
                }) { Text("Save", color = Zero.Chrome) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel", color = Zero.TextTertiary) }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Zero.Surface1,
            title = { Text("Delete playlist?", color = Zero.TextPrimary) },
            text = {
                Text(
                    "The playlist will be removed. Your audio files stay untouched.",
                    color = Zero.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { Graph.music.deletePlaylist(playlistId) }
                    showDelete = false
                    nav.pop()
                }) { Text("Delete", color = Zero.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel", color = Zero.TextTertiary) }
            },
        )
    }

    actionSong?.let { song ->
        SongActionsSheet(song = song, library = library, onDismiss = { actionSong = null })
    }
}

@Suppress("unused")
private val playlistHeaderShape = RoundedCornerShape(18.dp)
