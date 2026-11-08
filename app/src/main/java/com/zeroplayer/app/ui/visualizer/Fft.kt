package com.zeroplayer.app.ui.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Minimal in-place iterative radix-2 FFT used by the visualizer.
 * Operates on a preallocated buffer so frames allocate nothing.
 */
class Fft(private val size: Int) {

    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { kotlin.math.sin(-2.0 * PI * it / size).toFloat() }
    private val reverse = IntArray(size) { Integer.reverse(it) ushr (32 - log2(size)) }

    fun transform(real: FloatArray, imaginary: FloatArray) {
        for (i in 0 until size) {
            val j = reverse[i]
            if (j > i) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = temp
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                var k = 0
                var index = start
                while (k < half) {
                    val tableIndex = k * step
                    val cosine = cosTable[tableIndex]
                    val sine = sinTable[tableIndex]
                    val evenIndex = index + half
                    val evenReal = real[evenIndex]
                    val evenImag = imaginary[evenIndex]
                    val oddReal = evenReal * cosine - evenImag * sine
                    val oddImag = evenReal * sine + evenImag * cosine
                    real[evenIndex] = real[index] - oddReal
                    imaginary[evenIndex] = imaginary[index] - oddImag
                    real[index] += oddReal
                    imaginary[index] += oddImag
                    index++
                    k++
                }
                start += length
            }
            length *= 2
        }
    }

    private fun log2(value: Int): Int {
        var v = value
        var result = 0
        while (v > 1) {
            v = v shr 1
            result++
        }
        return result
    }

    companion object {
        fun hannWindow(size: Int): FloatArray =
            FloatArray(size) { 0.5f * (1f - cos(2.0 * PI * it / (size - 1)).toFloat()) }

        fun magnitude(sample: Float): Float = sqrt(sample * sample)

        /** Converts linear amplitude to a 0..1 display value. */
        fun toDisplay(amplitude: Float): Float {
            if (amplitude <= 0f) return 0f
            val db = 20f * kotlin.math.log10(amplitude.toDouble()).toFloat()
            return ((db + 60f) / 60f).pow(1.35f).coerceIn(0f, 1f)
        }
    }
}
