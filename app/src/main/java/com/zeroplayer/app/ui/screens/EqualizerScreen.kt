package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroplayer.app.audio.EqualizerController
import com.zeroplayer.app.audio.EqPreset
import com.zeroplayer.app.audio.EqState
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.ui.asDbLabel
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.ZeroDivider
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero

@Composable
fun EqualizerScreen() {
    val nav = LocalNav.current
    val state by EqualizerController.state.collectAsStateWithLifecycle()
    val library by Graph.music.state.collectAsStateWithLifecycle()
    val playing by PlaybackController.state.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
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
                MicroLabel("Equalizer")
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(40.dp))
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ten band",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Zero.TextPrimary,
                        )
                        Text(
                            text = "31 Hz – 16 kHz · 32-bit float DSP",
                            style = MaterialTheme.typography.bodySmall,
                            color = Zero.TextTertiary,
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { EqualizerController.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Zero.Black,
                            checkedTrackColor = Zero.Chrome,
                            checkedBorderColor = Zero.Chrome,
                            uncheckedThumbColor = Zero.TextTertiary,
                            uncheckedTrackColor = Zero.Surface3,
                            uncheckedBorderColor = Zero.Hairline,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (playing.connected) {
                        "Applied live to the current output."
                    } else {
                        "Settings are saved and applied as soon as playback starts."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(18.dp))
                EqCurve(state = state)
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.bandsHz.forEachIndexed { index, frequency ->
                    BandSlider(
                        label = frequencyLabel(frequency),
                        gain = state.gainsDb.getOrElse(index) { 0f },
                        enabled = state.enabled,
                        onChange = { EqualizerController.setBand(index, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(18.dp))
                ZeroDivider()
                Spacer(Modifier.height(16.dp))
                EffectSlider(
                    title = "Bass boost",
                    valueLabel = "${state.bassBoost / 10}%",
                    value = state.bassBoost / 1000f,
                    enabled = state.enabled,
                    onChange = { EqualizerController.setBassBoost((it * 1000).toInt()) },
                )
                Spacer(Modifier.height(14.dp))
                EffectSlider(
                    title = "Virtualizer",
                    valueLabel = "${state.virtualizer / 10}%",
                    value = state.virtualizer / 1000f,
                    enabled = state.enabled,
                    onChange = { EqualizerController.setVirtualizer((it * 1000).toInt()) },
                )
                Spacer(Modifier.height(14.dp))
                EffectSlider(
                    title = "Pre-amp",
                    valueLabel = state.preampDb.asDbLabel(),
                    value = (state.preampDb + 12f) / 18f,
                    enabled = state.enabled,
                    onChange = { EqualizerController.setPreamp(it * 18f - 12f) },
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        item {
            MicroLabel("Presets", modifier = Modifier.padding(start = 22.dp, bottom = 8.dp))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(EqualizerController.presets, key = { it.name }) { preset ->
                    PresetChip(
                        preset = preset,
                        selected = state.presetName == preset.name,
                        onClick = { EqualizerController.applyPreset(preset) },
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel("Saved profiles", modifier = Modifier.weight(1f))
                    OutLinePlayRow(
                        icon = Icons.Rounded.Save,
                        label = "Save",
                        onClick = {
                            profileName = state.presetName.ifBlank { "Custom" }
                            showSaveDialog = true
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (library.eqProfiles.isEmpty()) {
                    Text(
                        text = "No profiles yet. Tune the bands and save your own curve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Zero.TextTertiary,
                    )
                }
                library.eqProfiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { EqualizerController.applyProfile(profile) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = Zero.TextPrimary,
                            )
                            Text(
                                text = if (profile.builtin) "Factory preset" else "Custom curve",
                                style = MaterialTheme.typography.labelSmall,
                                color = Zero.TextTertiary,
                            )
                        }
                        if (!profile.builtin) {
                            ZeroIconButton(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete profile",
                                tint = Zero.TextTertiary,
                                size = 34.dp,
                                iconSize = 17.dp,
                                onClick = { EqualizerController.deleteProfile(profile.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutLinePlayRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Reset to flat",
                    onClick = { EqualizerController.reset() },
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Zero.Surface1,
            title = { Text("Save equalizer profile", color = Zero.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
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
                    EqualizerController.saveProfile(profileName)
                    showSaveDialog = false
                }) { Text("Save", color = Zero.Chrome) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = Zero.TextTertiary) }
            },
        )
    }
}

@Composable
private fun EqCurve(state: EqState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zero.Surface1)
            .border(0.7.dp, Zero.Hairline, RoundedCornerShape(16.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            val midY = size.height / 2f
            // 0 dB reference line
            drawLine(
                color = Zero.Hairline,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1f,
            )
            drawLine(
                color = Zero.Hairline.copy(alpha = 0.4f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 0.8f,
            )
            drawLine(
                color = Zero.Hairline.copy(alpha = 0.4f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 0.8f,
            )

            val gains = state.gainsDb
            if (gains.isEmpty()) return@Canvas
            val stepX = size.width / (gains.size - 1).coerceAtLeast(1)
            val path = Path()
            val fill = Path()
            fill.moveTo(0f, midY)
            gains.forEachIndexed { index, gain ->
                val x = stepX * index
                val y = midY - (gain / 12f) * (size.height / 2f)
                if (index == 0) {
                    path.moveTo(x, y)
                    fill.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, midY)
            fill.close()

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(Zero.Silver.copy(alpha = 0.20f), Zero.Silver.copy(alpha = 0.02f)),
                ),
            )
            drawPath(
                path = path,
                brush = Zero.silverGradient,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round),
            )
            gains.forEachIndexed { index, gain ->
                val x = stepX * index
                val y = midY - (gain / 12f) * (size.height / 2f)
                drawCircle(color = Zero.Chrome, radius = 2.6f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun BandSlider(
    label: String,
    gain: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minGain = -12f
    val maxGain = 12f
    val height = 172.dp

    fun gainFor(y: Float, totalHeight: Float): Float {
        val fraction = (1f - (y / totalHeight)).coerceIn(0f, 1f)
        return minGain + fraction * (maxGain - minGain)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> onChange(gainFor(offset.y, size.height.toFloat())) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset -> onChange(gainFor(offset.y, size.height.toFloat())) },
                    ) { change, _ ->
                        change.consume()
                        onChange(gainFor(change.position.y, size.height.toFloat()))
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val zeroY = size.height * (maxGain / (maxGain - minGain))
                val valueY = zeroY - (gain / maxGain) * (size.height / 2f)

                drawRoundRect(
                    color = Zero.Surface3,
                    topLeft = Offset(centerX - 1.5f, 0f),
                    size = Size(3f, size.height),
                    cornerRadius = CornerRadius(2f),
                )
                drawLine(
                    color = Zero.Silver.copy(alpha = 0.35f),
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = if (enabled) Zero.Chrome else Zero.TextTertiary,
                    start = Offset(centerX, zeroY),
                    end = Offset(centerX, valueY),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = if (enabled) Zero.Chrome else Zero.TextTertiary,
                    radius = 6f,
                    center = Offset(centerX, valueY),
                )
                drawCircle(
                    color = Zero.Black,
                    radius = 2.4f,
                    center = Offset(centerX, valueY),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${if (gain > 0.05f) "+" else ""}${(kotlin.math.round(gain * 10f) / 10f)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (gain == 0f) Zero.TextTertiary else Zero.Silver,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = Zero.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EffectSlider(
    title: String,
    valueLabel: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) Zero.TextPrimary else Zero.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Zero.TextTertiary,
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { if (enabled) onChange(it) },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Zero.Chrome else Zero.TextTertiary,
                activeTrackColor = Zero.Silver.copy(alpha = if (enabled) 1f else 0.3f),
                inactiveTrackColor = Zero.Surface3,
                disabledActiveTrackColor = Zero.Surface3,
                disabledInactiveTrackColor = Zero.Surface3,
            ),
        )
    }
}

@Composable
private fun PresetChip(preset: EqPreset, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Zero.Surface3 else Zero.Surface1)
            .border(
                width = 0.8.dp,
                color = if (selected) Zero.Silver.copy(alpha = 0.5f) else Zero.Hairline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Zero.Chrome else Zero.TextSecondary,
        )
    }
}

private fun frequencyLabel(hz: Float): String = when {
    hz >= 1000f -> "${(hz / 1000f).toInt()}k"
    else -> hz.toInt().toString()
}
