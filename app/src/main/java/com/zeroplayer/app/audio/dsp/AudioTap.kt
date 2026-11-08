package com.zeroplayer.app.audio.dsp

import kotlin.math.abs

/**
 * Lock-light ring buffer that carries the final PCM stream from the audio
 * renderer to the visualizer. A single audio thread writes, the UI thread reads
 * a snapshot on demand, so the visualizer costs nothing when it is not visible.
 */
object AudioTap {
    const val CAPACITY = 4096
    private const val MASK = CAPACITY - 1

    private val ring = FloatArray(CAPACITY)

    @Volatile
    var writeIndex: Long = 0L
        private set

    @Volatile
    var active: Boolean = false

    fun push(sample: Float) {
        ring[(writeIndex.toInt()) and MASK] = sample
        writeIndex += 1
    }

    /** Copies the most recent [count] samples into [out]. */
    fun snapshot(out: FloatArray): Boolean {
        val end = writeIndex
        val count = out.size.coerceAtMost(CAPACITY)
        if (end < count) {
            out.fill(0f)
            return false
        }
        var index = (end - count).toInt()
        for (i in 0 until count) {
            out[i] = ring[(index + i) and MASK]
        }
        return true
    }

    fun clear() {
        ring.fill(0f)
        writeIndex = 0L
    }

    /** Linear peak of the most recent window, for the level meter. */
    fun peak(window: Int = 1024): Float {
        var peak = 0f
        val end = writeIndex
        val count = window.coerceAtMost(CAPACITY)
        if (end < count) return 0f
        var index = (end - count).toInt()
        for (i in 0 until count) {
            val value = ring[(index + i) and MASK]
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }
}
