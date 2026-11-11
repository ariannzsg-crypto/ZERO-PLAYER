package com.zeroplayer.app.playback

import android.content.Context
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.zeroplayer.app.audio.dsp.TenBandEqProcessor
import com.zeroplayer.app.core.DiagLog

/**
 * Wires the ZERO PLAYER DSP chain (ten band EQ -> bass boost -> stereo widener)
 * into ExoPlayer's audio sink. The chain runs before the sonic processor so
 * speed/pitch changes stay sample accurate.
 */
class EqRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val chain = DefaultAudioSink.DefaultAudioProcessorChain(
            arrayOf(TenBandEqProcessor()),
            SilenceSkippingAudioProcessor(),
            SonicAudioProcessor(),
        )
        DiagLog.d(
            "EQ",
            "audio sink built: floatOutput=$enableFloatOutput " +
                "playbackParams=$enableAudioTrackPlaybackParams chain=eq,silence-skip,sonic",
        )
        return DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(chain)
            .build()
    }
}
