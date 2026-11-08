package com.zeroplayer.app.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct-form-I biquad section used by the ZERO PLAYER ten band equalizer.
 *
 * Coefficients follow the RBJ audio EQ cookbook. The class is deliberately free
 * of Android dependencies so the DSP maths can be verified independently.
 */
class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    val isBypassed: Boolean
        get() = b0 == 1f && b1 == 0f && b2 == 0f && a1 == 0f && a2 == 0f

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    fun setIdentity() {
        b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
    }

    /** Constant-0 dB peak / notch style band. */
    fun setPeaking(sampleRate: Int, frequencyHz: Float, q: Float, gainDb: Float) {
        if (gainDb == 0f) {
            setIdentity()
            return
        }
        val fs = sampleRate.toDouble().coerceAtLeast(8_000.0)
        val f = frequencyHz.coerceIn(20f, (fs / 2.0 - 100.0).toFloat()).toDouble()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / fs
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha / a
        b0 = ((1.0 + alpha * a) / a0).toFloat()
        b1 = ((-2.0 * cos(w0)) / a0).toFloat()
        b2 = ((1.0 - alpha * a) / a0).toFloat()
        a1 = ((-2.0 * cos(w0)) / a0).toFloat()
        a2 = ((1.0 - alpha / a) / a0).toFloat()
    }

    /** Low shelf, used for the bass boost control. */
    fun setLowShelf(sampleRate: Int, frequencyHz: Float, gainDb: Float, slope: Float = 1f) {
        if (gainDb == 0f) {
            setIdentity()
            return
        }
        val fs = sampleRate.toDouble().coerceAtLeast(8_000.0)
        val f = frequencyHz.coerceIn(20f, (fs / 2.0 - 100.0).toFloat()).toDouble()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / fs
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
        b0 = ((a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)) / a0).toFloat()
        b1 = ((2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)) / a0).toFloat()
        b2 = ((a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)) / a0).toFloat()
        a1 = ((-2.0 * ((a - 1.0) + (a + 1.0) * cosW)) / a0).toFloat()
        a2 = (((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha) / a0).toFloat()
    }

    /** High shelf, kept for completeness of the filter toolbox. */
    fun setHighShelf(sampleRate: Int, frequencyHz: Float, gainDb: Float, slope: Float = 1f) {
        if (gainDb == 0f) {
            setIdentity()
            return
        }
        val fs = sampleRate.toDouble().coerceAtLeast(8_000.0)
        val f = frequencyHz.coerceIn(20f, (fs / 2.0 - 100.0).toFloat()).toDouble()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / fs
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
        b0 = ((a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)) / a0).toFloat()
        b1 = ((-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)) / a0).toFloat()
        b2 = ((a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)) / a0).toFloat()
        a1 = ((2.0 * ((a - 1.0) - (a + 1.0) * cosW)) / a0).toFloat()
        a2 = (((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha) / a0).toFloat()
    }

    fun process(sample: Float): Float {
        val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = out
        return out
    }

    /** Magnitude response in dB at [frequencyHz]; used by diagnostics and tests. */
    fun magnitudeDb(sampleRate: Int, frequencyHz: Float): Float {
        val w = 2.0 * PI * frequencyHz / sampleRate
        val cos1 = cos(w)
        val cos2 = cos(2 * w)
        val sin1 = sin(w)
        val sin2 = sin(2 * w)
        val numReal = b0 + b1 * cos1 + b2 * cos2
        val numImag = -(b1 * sin1 + b2 * sin2)
        val denReal = 1.0 + a1 * cos1 + a2 * cos2
        val denImag = -(a1 * sin1 + a2 * sin2)
        val num = sqrt(numReal * numReal + numImag * numImag)
        val den = sqrt(denReal * denReal + denImag * denImag)
        if (den == 0.0) return 0f
        return (20.0 * kotlin.math.log10(num / den)).toFloat()
    }
}
