package com.zeroplayer.app.audio.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.zeroplayer.app.core.DiagLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Immutable snapshot of the user's EQ settings, published to the audio thread. */
class DspParams(
    val enabled: Boolean,
    val gainsDb: FloatArray,
    val preampDb: Float,
    val bassBoost: Int,
    val virtualizer: Int,
    val version: Long,
) {
    companion object {
        val BANDS_HZ = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        val DISABLED = DspParams(false, FloatArray(BANDS_HZ.size), 0f, 0, 0, 0L)
    }
}

/** Single writer (UI) / single reader (audio) holder. */
object EqDsp {
    @Volatile
    var params: DspParams = DspParams.DISABLED
        private set

    /**
     * Emergency switch. Set when the audio pipeline failed a runtime check with
     * the equalizer attached; the processor then reports itself inactive so
     * Media3 drops it from the chain and audio takes the stock path.
     */
    @Volatile
    var hardBypass: Boolean = false

    fun publish(enabled: Boolean, gainsDb: FloatArray, preampDb: Float, bassBoost: Int, virtualizer: Int) {
        if (enabled) hardBypass = false
        params = DspParams(
            enabled = enabled,
            gainsDb = gainsDb.copyOf(),
            preampDb = preampDb,
            bassBoost = bassBoost.coerceIn(0, 1_000),
            virtualizer = virtualizer.coerceIn(0, 1_000),
            version = params.version + 1,
        )
    }
}

/**
 * Ten band parametric equalizer, bass boost and stereo widener applied to the
 * decoded PCM stream. All processing happens in 32 bit float on 16 bit PCM, so
 * it works identically on every device regardless of the hardware EQ bands.
 */
class TenBandEqProcessor : BaseAudioProcessor() {

    private val bandCount = DspParams.BANDS_HZ.size

    // [channel][band]
    private val filters = Array(2) { Array(bandCount) { Biquad() } }
    private val bass = Array(2) { Biquad() }

    private var appliedVersion = -1L
    private var sampleRate = 44_100
    private var channels = 2
    private var passthrough = true
    private var loggedBypass = false

    /** Inactive while hard-bypassed, so the sink skips this processor entirely. */
    override fun isActive(): Boolean = !EqDsp.hardBypass

    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val format = inputAudioFormat
        passthrough = format.encoding != C.ENCODING_PCM_16BIT || format.channelCount > 2
        sampleRate = format.sampleRate.takeIf { it > 0 } ?: 44_100
        channels = if (passthrough) 2 else format.channelCount
        appliedVersion = -1L
        filters.forEach { channel -> channel.forEach { it.reset() } }
        bass.forEach { it.reset() }
        loggedBypass = false
        DiagLog.d(
            "EQ",
            "configure: encoding=${format.encoding} channels=${format.channelCount} " +
                "rate=${format.sampleRate} passthrough=$passthrough",
        )
        return format
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val params = EqDsp.params
        if (passthrough || !params.enabled) {
            copyThrough(inputBuffer)
            return
        }

        val start = inputBuffer.position()
        try {
            if (params.version != appliedVersion) {
                applyParams(params)
                appliedVersion = params.version
                DiagLog.d(
                    "EQ",
                    "params applied: version=${params.version} preamp=${params.preampDb} " +
                        "bass=${params.bassBoost} widen=${params.virtualizer}",
                )
            }

            val shortCount = inputBuffer.remaining() / 2
            val outBuffer: ByteBuffer = replaceOutputBuffer(inputBuffer.remaining())
            inputBuffer.order(ByteOrder.nativeOrder())
            outBuffer.order(ByteOrder.nativeOrder())

            val source = inputBuffer.asShortBuffer()
            val target = outBuffer.asShortBuffer()

            val mono = channels == 1
            val preamp = 10.0.pow(params.preampDb / 20.0).toFloat()
            val widen = if (params.virtualizer > 0) params.virtualizer / 1_000f * 0.85f else 0f

            val frames = shortCount / channels
            repeat(frames) {
                var left = source.get().toFloat() / 32_768f
                var right = if (mono) left else source.get().toFloat() / 32_768f

                left *= preamp
                right *= preamp

                for (band in 0 until bandCount) {
                    left = filters[0][band].process(left)
                    if (!mono) right = filters[1][band].process(right)
                }
                left = bass[0].process(left)
                if (!mono) right = bass[1].process(right)

                if (widen > 0f && !mono) {
                    val mid = (left + right) * 0.5f
                    val side = (left - right) * 0.5f * (1f + widen)
                    left = mid + side
                    right = mid - side
                }

                AudioTap.push((left + right) * 0.5f)

                target.put(toPcm16(left))
                target.put(toPcm16(right))
            }

            inputBuffer.position(inputBuffer.limit())
            // Samples were written through a short view, so the parent buffer's
            // position never moved. Publish the byte count explicitly instead of
            // flip(), which would expose an empty buffer and silence the track.
            outBuffer.position(0)
            outBuffer.limit(frames * channels * 2)
        } catch (t: Throwable) {
            DiagLog.e("EQ", "DSP failed, switching to bypass: ${DiagLog.describe(t)}", t)
            passthrough = true
            inputBuffer.position(start)
            copyThrough(inputBuffer)
        }
    }

    /** Copies the input to the output unchanged; the safe fallback path. */
    private fun copyThrough(inputBuffer: ByteBuffer) {
        if (!loggedBypass) {
            loggedBypass = true
            DiagLog.d("EQ", "bypass: passthrough=$passthrough enabled=${EqDsp.params.enabled}")
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
        inputBuffer.position(inputBuffer.limit())
    }

    private fun applyParams(params: DspParams) {
        val gains = params.gainsDb
        for (channel in 0 until 2) {
            for (band in 0 until bandCount) {
                val gain = gains.getOrElse(band) { 0f }.takeIf { it.isFinite() } ?: 0f
                filters[channel][band].setPeaking(sampleRate, DspParams.BANDS_HZ[band], Q, gain)
            }
            val bassGain = (params.bassBoost / 1_000f * 12f).takeIf { it.isFinite() } ?: 0f
            bass[channel].setLowShelf(sampleRate, 105f, bassGain)
        }
    }

    override fun onFlush() {
        filters.forEach { channel -> channel.forEach { it.reset() } }
        bass.forEach { it.reset() }
        appliedVersion = -1L
    }

    override fun onReset() {
        filters.forEach { channel -> channel.forEach { it.reset() } }
        bass.forEach { it.reset() }
        appliedVersion = -1L
    }

    private fun toPcm16(value: Float): Short {
        if (!value.isFinite()) return 0
        val scaled = value * 32_767f
        return when {
            scaled > 32_767f -> 32_767
            scaled < -32_768f -> -32_768
            else -> scaled.toInt().toShort()
        }
    }

    private companion object {
        /** Octave-spaced bands use a Q of ~1.41 for a gentle, musical overlap. */
        const val Q = 1.25f
    }
}
