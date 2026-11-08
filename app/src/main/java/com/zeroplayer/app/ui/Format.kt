package com.zeroplayer.app.ui

import java.util.Locale

/** 3:07 or 1:02:44 */
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatTotalDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes >= 1 -> "$minutes min"
        else -> "${millis / 1000} sec"
    }
}

fun trackCountLabel(count: Int): String = if (count == 1) "1 track" else "$count tracks"

fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

fun albumCountLabel(count: Int): String = if (count == 1) "1 album" else "$count albums"

fun Float.asPercent(): String = "${(this * 100).toInt()}%"

fun Float.asDbLabel(): String {
    val rounded = (kotlin.math.round(this * 10f) / 10f)
    return if (rounded > 0) "+${rounded} dB" else "${rounded} dB"
}
