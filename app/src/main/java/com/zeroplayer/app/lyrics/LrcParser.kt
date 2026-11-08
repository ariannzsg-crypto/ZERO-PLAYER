package com.zeroplayer.app.lyrics

/** A single timed lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/** Parsed lyrics, timed or plain. */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    val plainText: String get() = lines.joinToString("\n") { it.text }

    /** Index of the line that should be highlighted at [positionMs]. */
    fun activeIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics(emptyList(), false, "none")
    }
}

/**
 * Parses LRC (both `.lrc` files and inline synced lyrics) plus plain text.
 * Supports multiple timestamps per line, offset tags and metadata headers.
 */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, source: String = "file"): Lyrics {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return Lyrics.EMPTY

        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val entries = ArrayList<LyricLine>(128)

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matches = timeTag.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                // Metadata tags such as [ar:...] are skipped; other text is kept untimed.
                if (!trimmed.startsWith("[") || !trimmed.contains(':')) {
                    entries += LyricLine(-1L, trimmed)
                }
                continue
            }

            val content = trimmed.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0L
                    2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val timeMs = minutes * 60_000L + seconds * 1_000L + fraction + offset
                if (content.isNotEmpty()) entries += LyricLine(timeMs.coerceAtLeast(0L), content)
            }
        }

        val timed = entries.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
        if (timed.size >= 2) {
            return Lyrics(timed, synced = true, source = source)
        }

        val plain = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !timeTag.containsMatchIn(it) }
            .toList()
        if (plain.isEmpty()) return Lyrics.EMPTY
        return Lyrics(plain.map { LyricLine(-1L, it) }, synced = false, source = source)
    }

    /** Serialises timed lyrics back to canonical LRC for export/sharing. */
    fun toLrc(lyrics: Lyrics): String {
        if (!lyrics.synced) return lyrics.plainText
        return lyrics.lines.joinToString("\n") { line ->
            val totalSeconds = line.timeMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val centis = (line.timeMs % 1000) / 10
            "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
        }
    }
}
