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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.BuildConfig
import com.zeroplayer.app.audio.EqualizerController
import com.zeroplayer.app.core.DiagLog
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.LibraryData
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.ZeroDivider
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.formatTotalDuration
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(library: LibraryData, playback: PlaybackState) {
    val nav = LocalNav.current
    val eqState by EqualizerController.state.collectAsStateWithLifecycle()
    var speed by remember { mutableStateOf(playback.speed) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var logText by remember { mutableStateOf(DiagLog.dump()) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val bottomInset = LocalBottomInset.current

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
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZeroIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = { nav.pop() },
                )
                Spacer(Modifier.weight(1f))
                MicroLabel("Settings")
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(40.dp))
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Text("Settings", style = MaterialTheme.typography.displayMedium, color = Zero.TextPrimary)
                Spacer(Modifier.height(4.dp))
                MicroLabel("Offline · no account · optional AI")
                Spacer(Modifier.height(20.dp))
            }
        }

        item { SettingsSectionLabel("Library") }
        item {
            SettingsRow(
                icon = Icons.Rounded.Refresh,
                title = "Rescan library",
                subtitle = when {
                    library.scanning -> "Scanning…"
                    library.lastScanAt > 0 -> "Last scan " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                        .format(Date(library.lastScanAt))
                    else -> "Not scanned yet"
                },
                onClick = { Graph.music.refresh() },
            )
        }
        item {
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Indexed tracks",
                subtitle = "${library.songs.size} songs · ${library.albums.size} albums · " +
                    formatTotalDuration(library.songs.sumOf { it.durationMs }),
                showChevron = false,
                onClick = {},
            )
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsSectionLabel("Playback") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Zero.Surface1)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Playback speed", style = MaterialTheme.typography.titleSmall, color = Zero.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${speed}x", style = MaterialTheme.typography.labelMedium, color = Zero.Silver)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        ChoiceChip(
                            label = if (option == 1f) "1x" else "${option}x",
                            selected = speed == option,
                            onClick = {
                                speed = option
                                PlaybackController.setSpeed(option)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Zero.Surface1)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Sleep timer", style = MaterialTheme.typography.titleSmall, color = Zero.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (sleepMinutes == 0) "Off" else "$sleepMinutes min",
                        style = MaterialTheme.typography.labelMedium,
                        color = Zero.Silver,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 60).forEach { minutes ->
                        ChoiceChip(
                            label = if (minutes == 0) "Off" else "$minutes",
                            selected = sleepMinutes == minutes,
                            onClick = {
                                sleepMinutes = minutes
                                PlaybackController.setSleepTimer(minutes)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsSectionLabel("Audio") }
        item {
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Equalizer",
                subtitle = if (eqState.enabled) "On · ${eqState.presetName}" else "Off",
                onClick = { nav.push(Dest.Equalizer) },
            )
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsSectionLabel("AI assist") }
        item {
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "AI assistant",
                subtitle = if (Graph.prefs.aiApiKey.isBlank()) {
                    "Not configured · lyrics lookup and artist playlists"
                } else {
                    "On · ${Graph.prefs.aiModel}"
                },
                onClick = { nav.push(Dest.Ai) },
            )
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsSectionLabel("Diagnostics") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Zero.Surface1)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (playback.connected) "Player connected" else "Player not connected",
                        style = MaterialTheme.typography.titleSmall,
                        color = Zero.TextPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${playback.positionMs / 1000}s / ${playback.durationMs / 1000}s · " +
                            "queue ${playback.queue.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Zero.TextTertiary,
                    )
                }
                playback.error?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = Zero.Silver)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = "Copy",
                        selected = copied,
                        onClick = {
                            clipboard.setText(AnnotatedString(DiagLog.dump()))
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = "Refresh",
                        selected = false,
                        onClick = {
                            logText = DiagLog.dump()
                            copied = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = "Clear",
                        selected = false,
                        onClick = {
                            DiagLog.clear()
                            logText = DiagLog.dump()
                            copied = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsSectionLabel("About") }
        item {
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "ZERO PLAYER",
                subtitle = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                showChevron = false,
                onClick = {},
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                ZeroDivider()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "ZERO PLAYER plays audio stored on this device. Playlists, favourites and statistics " +
                        "live only in a local database and there are no trackers. The network permission exists " +
                        "solely for the optional AI assistant you configure yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Built with Kotlin, Jetpack Compose, Media3 and Room. " +
                        "Ten band equalizer, bass boost and the visualizer run in-app on the PCM stream.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    MicroLabel(
        text = text,
        modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showChevron, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Zero.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Zero.TextTertiary)
        }
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Zero.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Zero.Surface3 else Zero.Surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Zero.Chrome else Zero.TextTertiary,
        )
    }
}

