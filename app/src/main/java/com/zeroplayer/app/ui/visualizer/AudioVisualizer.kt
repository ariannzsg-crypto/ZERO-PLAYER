package com.zeroplayer.app.ui.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.audio.dsp.AudioTap
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class VisualizerMode { OFF, BARS, CIRCLE, WAVE }

/**
 * Real time audio visualizer fed by the PCM tap inside the playback DSP chain.
 *
 * Only the final 1024 samples are copied per frame and the FFT buffer is reused,
 * so a frame costs a few microseconds and no allocations. Rendering stops as soon
 * as the composable leaves composition or the track is paused.
 */
private const val FFT_SIZE = 1024
private const val BAR_COUNT = 48

@Composable
fun AudioVisualizer(
    mode: VisualizerMode,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Zero.Chrome,
) {
    if (mode == VisualizerMode.OFF) return

    val fft = remember { Fft(FFT_SIZE) }
    val window = remember { Fft.hannWindow(FFT_SIZE) }
    val samples = remember { FloatArray(FFT_SIZE) }
    val real = remember { FloatArray(FFT_SIZE) }
    val imaginary = remember { FloatArray(FFT_SIZE) }
    val smoothed = remember { FloatArray(BAR_COUNT) }
    var bands by remember { mutableStateOf(FloatArray(BAR_COUNT)) }
    var waveform by remember { mutableStateOf(FloatArray(256)) }

    LaunchedEffect(mode, isPlaying) {
        while (isActive) {
            val hasData = AudioTap.snapshot(samples)
            if (hasData && isPlaying) {
                for (i in 0 until FFT_SIZE) {
                    real[i] = samples[i] * window[i]
                    imaginary[i] = 0f
                }
                fft.transform(real, imaginary)

                for (bar in 0 until BAR_COUNT) {
                    val low = binFor(bar, BAR_COUNT / 2f, FFT_SIZE)
                    val high = binFor(bar + 1, BAR_COUNT / 2f, FFT_SIZE)
                    var peak = 0f
                    for (bin in low until maxOf(high, low + 1)) {
                        if (bin >= FFT_SIZE / 2) break
                        val magnitude = hypot(real[bin], imaginary[bin]) / (FFT_SIZE / 4f)
                        if (magnitude > peak) peak = magnitude
                    }
                    val target = Fft.toDisplay(peak)
                    smoothed[bar] = if (target > smoothed[bar]) {
                        smoothed[bar] + (target - smoothed[bar]) * 0.55f
                    } else {
                        smoothed[bar] + (target - smoothed[bar]) * 0.18f
                    }
                }
                bands = smoothed.copyOf()

                val step = FFT_SIZE / 256
                waveform = FloatArray(256) { index -> samples[index * step] }
            } else {
                for (bar in 0 until BAR_COUNT) {
                    smoothed[bar] *= 0.90f
                }
                bands = smoothed.copyOf()
                waveform = FloatArray(256) { index -> waveform.getOrElse(index) { 0f } * 0.9f }
            }

            kotlinx.coroutines.delay(16)
        }
    }

    val phase by rememberInfiniteTransition(label = "phase").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rotation",
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                VisualizerMode.BARS -> {
                    val gap = size.width * 0.008f
                    val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
                    bands.forEachIndexed { index, value ->
                        val height = (size.height * (0.06f + value * 0.94f)).coerceAtLeast(size.height * 0.02f)
                        val x = index * (barWidth + gap)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.25f)),
                                startY = size.height - height,
                                endY = size.height,
                            ),
                            topLeft = Offset(x, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                        )
                    }
                }

                VisualizerMode.CIRCLE -> {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = minOf(size.width, size.height) * 0.34f
                    val maxBar = minOf(size.width, size.height) * 0.13f
                    drawCircle(
                        color = color.copy(alpha = 0.05f),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 1.2f),
                    )
                    bands.forEachIndexed { index, value ->
                        val angle = (index.toFloat() / BAR_COUNT) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                        val length = 3f + maxBar * value
                        val startOffset = Offset(
                            center.x + cos(angle) * baseRadius,
                            center.y + sin(angle) * baseRadius,
                        )
                        val endOffset = Offset(
                            center.x + cos(angle) * (baseRadius + length),
                            center.y + sin(angle) * (baseRadius + length),
                        )
                        drawLine(
                            color = color.copy(alpha = 0.25f + value * 0.75f),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color.copy(alpha = 0.10f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.8f,
                        ),
                        radius = baseRadius * 1.8f,
                        center = center,
                    )
                }

                VisualizerMode.WAVE -> {
                    val midY = size.height / 2f
                    val path = Path()
                    val points = 128
                    for (index in 0 until points) {
                        val sourceIndex = (index * waveform.size / points).coerceIn(0, waveform.lastIndex)
                        val x = size.width * index / (points - 1f)
                        val amplitude = waveform[sourceIndex].coerceIn(-1f, 1f)
                        val y = midY + amplitude * size.height * 0.42f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.2f), color, color.copy(alpha = 0.2f)),
                        ),
                        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = color.copy(alpha = 0.10f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1f,
                    )
                    // Phase marker keeps the wave alive on silence.
                    val markerX = size.width * (0.5f + 0.5f * cos(phase))
                    drawCircle(
                        color = color.copy(alpha = 0.35f),
                        radius = 2.5f,
                        center = Offset(markerX, midY),
                    )
                }

                VisualizerMode.OFF -> Unit
            }
        }
    }
}

/** Log-spaced bin mapping so bass does not dominate the display. */
private fun binFor(bar: Int, barCount: Float, fftSize: Int): Int {
    val maxBin = fftSize / 2
    val normalized = bar / barCount
    val mapped = (maxBin * normalized.pow(2.1f)).toInt()
    return mapped.coerceIn(0, maxBin - 1)
}

@Suppress("unused")
private val visualizerDensity = 1.dp

@Suppress("unused")
private fun absHelper(value: Float) = abs(value)
