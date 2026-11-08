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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.EmptyState
import com.zeroplayer.app.ui.components.EqualizerBars
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.formatDuration
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero

@Composable
fun QueueScreen(playback: PlaybackState) {
    val nav = LocalNav.current
    val listState = rememberLazyListState()

    LaunchedEffect(playback.index, playback.queue.size) {
        if (playback.index in 0 until playback.queue.size) {
            listState.animateScrollToItem(playback.index.coerceAtLeast(0))
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
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = { nav.pop() },
            )
            Spacer(Modifier.weight(1f))
            MicroLabel("Queue")
            Spacer(Modifier.weight(1f))
            ZeroIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Clear queue",
                onClick = { PlaybackController.clearQueue() },
                tint = Zero.TextTertiary,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.headlineLarge,
                color = Zero.TextPrimary,
            )
            Text(
                text = "${playback.queue.size} tracks in the queue",
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
            )
            Spacer(Modifier.height(14.dp))
            if (playback.queue.isNotEmpty()) {
                OutLinePlayRow(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Shuffle queue",
                    onClick = { PlaybackController.toggleShuffle() },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (playback.queue.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.QueueMusic,
                title = "The queue is empty",
                message = "Start playback from any album, playlist or search result.",
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(playback.queue.size, key = { index -> "queue_${index}_${playback.queue[index].songId}" }) { index ->
                    val entry = playback.queue[index]
                    val isCurrent = index == playback.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { PlaybackController.seekToQueueItem(index) }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CoverArt(
                                artworkUri = entry.artworkUri,
                                label = entry.title,
                                corner = 8.dp,
                                modifier = Modifier.size(44.dp),
                            )
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Zero.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EqualizerBars(
                                        active = playback.isPlaying,
                                        barColor = Zero.Chrome,
                                        barWidth = 2.dp,
                                        height = 12.dp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title.ifBlank { "Track" },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) Zero.Chrome else Zero.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Zero.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Text(
                                text = formatDuration(playback.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Zero.TextTertiary,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        ZeroIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Remove from queue",
                            tint = Zero.TextTertiary.copy(alpha = 0.7f),
                            size = 32.dp,
                            iconSize = 15.dp,
                            onClick = { PlaybackController.removeQueueItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val queueArrangement = Arrangement.Center
