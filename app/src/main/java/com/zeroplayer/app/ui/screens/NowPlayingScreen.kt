package com.zeroplayer.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.formatDuration
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import com.zeroplayer.app.ui.visualizer.AudioVisualizer
import com.zeroplayer.app.ui.visualizer.VisualizerMode
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(playback: PlaybackState, library: LibraryData) {
    val nav = LocalNav.current
    var visualizerMode by remember { mutableStateOf(VisualizerMode.OFF) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    var isFavorite by remember(playback.songId) {
        mutableStateOf(library.isFavorite(playback.songId))
    }

    val song: Song? = remember(library.songs, playback.songId) {
        library.songs.firstOrNull { it.id == playback.songId }
    }

    // Breathing artwork while playing.
    var breathing by remember { mutableStateOf(false) }
    LaunchedEffect(playback.isPlaying) { breathing = playback.isPlaying }
    val artworkScale by animateFloatAsState(
        targetValue = if (breathing) 1.0f else 0.97f,
        animationSpec = tween(1200),
        label = "artworkScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 140.dp.toPx()) nav.pop()
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { change, amount ->
                        if (amount > 0 || dragOffset > 0) {
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                            change.consume()
                        }
                    },
                )
            },
    ) {
        // Ambient wash derived from the artwork position, keeping the canvas black.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0C0C10),
                            Zero.Black,
                            Zero.Black,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragOffset }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZeroIconButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close",
                    onClick = { nav.pop() },
                )
                Spacer(Modifier.weight(1f))
                MicroLabel("Now playing")
                Spacer(Modifier.weight(1f))
                ZeroIconButton(
                    icon = Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    onClick = { nav.push(Dest.Queue) },
                )
            }

            Spacer(Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                CoverArt(
                    artworkUri = playback.artworkUri ?: song?.artworkUri?.toString(),
                    label = playback.title,
                    corner = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(if (visualizerMode == VisualizerMode.OFF) 0.88f else 0.62f)
                        .aspectRatio(1f),
                )
                if (visualizerMode != VisualizerMode.OFF) {
                    AudioVisualizer(
                        mode = visualizerMode,
                        isPlaying = playback.isPlaying,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (visualizerMode == VisualizerMode.CIRCLE) Zero.Silver else Zero.Chrome,
                    )
                }
            }

            Spacer(Modifier.weight(0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playback.title.ifBlank { "Nothing playing" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Zero.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = playback.artist.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Zero.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ZeroIconButton(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Zero.Chrome else Zero.SilverDim,
                    onClick = {
                        val id = playback.songId
                        if (id > 0) {
                            isFavorite = !isFavorite
                            Graph.appScope.launch { Graph.music.toggleFavorite(id) }
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            SeekBar(playback = playback)

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZeroIconButton(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.shuffle) Zero.Chrome else Zero.SilverDim,
                    onClick = { PlaybackController.toggleShuffle() },
                )
                ZeroIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    iconSize = 32.dp,
                    onClick = { PlaybackController.previous() },
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Zero.chromeGradient)
                        .clickable { PlaybackController.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = Zero.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
                ZeroIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    iconSize = 32.dp,
                    onClick = { PlaybackController.next() },
                )
                ZeroIconButton(
                    icon = if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        Zero.SilverDim
                    } else {
                        Zero.Chrome
                    },
                    onClick = { PlaybackController.cycleRepeat() },
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerShortcut(Icons.Rounded.Lyrics, "Lyrics", active = false) {
                    nav.push(Dest.Lyrics)
                }
                PlayerShortcut(Icons.Rounded.GraphicEq, "Equalizer", active = false) {
                    nav.push(Dest.Equalizer)
                }
                PlayerShortcut(
                    icon = Icons.Rounded.Album,
                    label = "Visualizer",
                    active = visualizerMode != VisualizerMode.OFF,
                ) {
                    visualizerMode = when (visualizerMode) {
                        VisualizerMode.OFF -> VisualizerMode.BARS
                        VisualizerMode.BARS -> VisualizerMode.CIRCLE
                        VisualizerMode.CIRCLE -> VisualizerMode.WAVE
                        VisualizerMode.WAVE -> VisualizerMode.OFF
                    }
                }
                PlayerShortcut(Icons.Rounded.QueueMusic, "Queue", active = false) {
                    nav.push(Dest.Queue)
                }
            }
        }
    }
}

@Composable
private fun PlayerShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Zero.Chrome else Zero.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Zero.Silver else Zero.TextTertiary,
        )
    }
}

@Composable
private fun SeekBar(playback: PlaybackState) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = playback.durationMs.coerceAtLeast(1L)
    val value = if (dragging) dragValue else playback.positionMs.toFloat()

    Column {
        Slider(
            value = value.coerceIn(0f, max.toFloat()),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                PlaybackController.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Zero.Chrome,
                activeTrackColor = Zero.Silver,
                inactiveTrackColor = Zero.Surface3,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragging) dragValue.toLong() else playback.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
            )
            Text(
                text = "-${formatDuration((playback.durationMs - playback.positionMs).coerceAtLeast(0))}",
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
            )
        }
    }
}

@Suppress("unused")
private val nowPlayingBorder = Modifier.border(0.7.dp, Zero.Hairline, RoundedCornerShape(24.dp))
