package com.zeroplayer.app.ui.components

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Playlist
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.launch

/**
 * Track context sheet: queue, playlist and favourite actions that actually
 * mutate the player and database (no dead entries).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    library: LibraryData,
    onDismiss: () -> Unit,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onEditTagsInfo: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showPlaylists by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isFavorite = library.isFavorite(song.id)
    val context = LocalContext.current

    // Android 11+ asks the user to confirm a MediaStore delete request; older
    // releases delete directly and report when the platform refuses.
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                Graph.music.purgeSongs(listOf(song.id))
                sheetState.hide()
                onDismiss()
            }
        }
    }

    fun requestFileDelete() {
        val uri = song.contentUri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            return
        }
        val removed = runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
        if (removed > 0) {
            scope.launch {
                Graph.music.purgeSongs(listOf(song.id))
                sheetState.hide()
                onDismiss()
            }
        } else {
            Toast.makeText(context, "Android did not allow deleting this file.", Toast.LENGTH_LONG).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Zero.Surface1,
        contentColor = Zero.TextPrimary,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(bottom = 26.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    artworkUri = song.artworkUri.toString(),
                    label = song.title,
                    corner = 10.dp,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${song.artist} · ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Zero.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            ZeroDivider()

            if (!showPlaylists) {
                SheetAction(Icons.Rounded.PlaylistPlay, "Play next") {
                    PlaybackController.addToQueueNext(song)
                    onDismiss()
                }
                SheetAction(Icons.Rounded.QueueMusic, "Add to queue") {
                    PlaybackController.addToQueueEnd(song)
                    onDismiss()
                }
                SheetAction(Icons.Rounded.PlaylistAdd, "Add to playlist") {
                    showPlaylists = true
                }
                SheetAction(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Zero.Chrome else Zero.TextPrimary,
                ) {
                    Graph.appScope.launch { Graph.music.toggleFavorite(song.id) }
                    onDismiss()
                }
                if (onGoToAlbum != null) {
                    SheetAction(Icons.Rounded.QueueMusic, "Go to album") {
                        onGoToAlbum()
                        onDismiss()
                    }
                }
                if (onGoToArtist != null) {
                    SheetAction(Icons.Rounded.QueueMusic, "Go to artist") {
                        onGoToArtist()
                        onDismiss()
                    }
                }
                SheetAction(Icons.Rounded.DeleteOutline, "Delete from device", tint = Zero.Danger) {
                    showDeleteConfirm = true
                }
            } else {
                Text(
                    text = "ADD TO PLAYLIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = Zero.TextTertiary,
                    modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp),
                )
                SheetAction(Icons.Rounded.Add, "New playlist") {
                    scope.launch {
                        val id = Graph.music.createPlaylist("New playlist")
                        Graph.music.addToPlaylist(id, listOf(song.id))
                        sheetState.hide()
                        onDismiss()
                    }
                }
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(library.playlists.filter { it.smartKind == null }, key = { it.id }) { playlist ->
                        PlaylistPickRow(playlist) {
                            scope.launch {
                                Graph.music.addToPlaylist(playlist.id, listOf(song.id))
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Zero.Surface1,
            title = { Text("Delete this file?", color = Zero.TextPrimary) },
            text = {
                Text(
                    "“${song.title}” will be removed from your device storage, together with its " +
                        "playlist entries. This cannot be undone.",
                    color = Zero.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    requestFileDelete()
                }) { Text("Delete", color = Zero.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Zero.TextTertiary)
                }
            },
        )
    }
}

/** Applies one playlist to a multi-song selection in a single write. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(songIds: List<Long>, library: LibraryData, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Zero.Surface1,
        contentColor = Zero.TextPrimary,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(bottom = 26.dp)) {
            Text(
                text = "ADD ${songIds.size} TRACK" + if (songIds.size == 1) "" else "S" + " TO",
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
                modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 8.dp),
            )
            SheetAction(Icons.Rounded.Add, "New playlist") {
                scope.launch {
                    val id = Graph.music.createPlaylist("New playlist")
                    Graph.music.addToPlaylist(id, songIds)
                    sheetState.hide()
                    onDismiss()
                }
            }
            LazyColumn(modifier = Modifier.height(260.dp)) {
                items(library.playlists.filter { it.smartKind == null }, key = { it.id }) { playlist ->
                    PlaylistPickRow(playlist) {
                        scope.launch {
                            Graph.music.addToPlaylist(playlist.id, songIds)
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Zero.Surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = Zero.TextPrimary)
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = Zero.TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
        )
    }
}

/** Row of horizontally scrollable artwork cards. */
@Composable
fun ArtworkCard(
    title: String,
    subtitle: String,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick),
    ) {
        CoverArt(
            artworkUri = artworkUri,
            label = title,
            corner = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(width),
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Zero.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Zero.Surface1)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Zero.Silver, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Zero.TextSecondary)
    }
}
