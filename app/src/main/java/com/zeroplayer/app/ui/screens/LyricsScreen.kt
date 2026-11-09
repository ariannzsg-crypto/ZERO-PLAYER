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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.lyrics.Lyrics
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.launch

@Composable
fun LyricsScreen(playback: PlaybackState, library: LibraryData) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song = remember(library.songs, playback.songId) { library.songs.firstOrNull { it.id == playback.songId } }

    var lyrics by remember(playback.songId) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(playback.songId) { mutableStateOf(true) }
    var aiBusy by remember(playback.songId) { mutableStateOf(false) }
    var aiStatus by remember(playback.songId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val currentSong = song ?: return@rememberLauncherForActivityResult
        scope.launch {
            val raw = Graph.lyrics.readUri(uri)
            if (!raw.isNullOrBlank()) {
                Graph.lyrics.saveImportedLyrics(currentSong.id, raw)
                lyrics = Graph.lyrics.lyricsFor(currentSong)
            }
        }
    }

    LaunchedEffect(playback.songId) {
        val currentSong = song
        if (currentSong == null) {
            lyrics = Lyrics.EMPTY
            loading = false
            return@LaunchedEffect
        }
        loading = true
        lyrics = Graph.lyrics.lyricsFor(currentSong)
        loading = false
    }

    val activeIndex = lyrics?.activeIndex(playback.positionMs) ?: -1
    LaunchedEffect(activeIndex) {
        val data = lyrics ?: return@LaunchedEffect
        if (data.synced && activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZeroIconButton(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = "Back",
                onClick = { nav.pop() },
            )
            Spacer(Modifier.weight(1f))
            MicroLabel("Lyrics")
            Spacer(Modifier.weight(1f))
            ZeroIconButton(
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = "Find lyrics with AI",
                onClick = {
                    if (aiBusy) return@ZeroIconButton
                    val currentSong = song
                    when {
                        currentSong == null -> aiStatus = "No track is loaded."
                        !Graph.ai.isReady -> aiStatus = "Add an API key in Settings → AI first."
                        else -> scope.launch {
                            aiBusy = true
                            aiStatus = "Asking the assistant…"
                            val result = runCatching { Graph.ai.findLyrics(currentSong) }
                            aiBusy = false
                            result.onSuccess { text ->
                                Graph.lyrics.saveImportedLyrics(currentSong.id, text, source = "ai")
                                lyrics = Graph.lyrics.lyricsFor(currentSong)
                                aiStatus = null
                            }.onFailure {
                                aiStatus = it.message ?: "The AI request failed."
                            }
                        }
                    }
                },
            )
            ZeroIconButton(
                icon = Icons.Rounded.FileUpload,
                contentDescription = "Import LRC",
                onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = playback.title.ifBlank { "No track" },
                style = MaterialTheme.typography.headlineMedium,
                color = Zero.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = playback.artist.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = Zero.TextTertiary,
            )
            aiStatus?.let { status ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.Silver,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        val data = lyrics
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading lyrics…", color = Zero.TextTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            data == null || data.isEmpty -> {
                EmptyState(
                    icon = Icons.Rounded.Lyrics,
                    title = "No lyrics yet",
                    message = "This track has no embedded lyrics and no .lrc file was found. " +
                        "Import an LRC file to sync lyrics with playback.",
                    actionLabel = "Import LRC",
                    onAction = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                )

            }

            else -> {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MicroLabel(if (data.synced) "Synced" else "Unsynced")
                    MicroLabel("·")
                    MicroLabel(data.source)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(data.lines, key = { index, _ -> "line_$index" }) { index, line ->
                        val isActive = data.synced && index == activeIndex
                        Text(
                            text = line.text.ifBlank { "♪" },
                            style = if (isActive) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            color = if (isActive) Zero.Chrome else Zero.TextSecondary.copy(alpha = 0.55f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = data.synced && line.timeMs >= 0) {
                                    if (line.timeMs >= 0) PlaybackController.seekTo(line.timeMs)
                                }
                                .padding(vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val lyricsBackTarget = Dest.Lyrics

@Suppress("unused")
private val unusedWidth = 0.dp
