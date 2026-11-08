package com.zeroplayer.app.lyrics

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads lyrics embedded in audio tags without any third party dependency:
 *
 *  - ID3v2.3 / ID3v2.4 `USLT` frames (MP3, and any file using ID3 tags)
 *  - `LYRICS` / `UNSYNCEDLYRICS` Vorbis comments (FLAC, OGG)
 *  - `©lyr` atom (M4A / AAC container)
 *
 * Returns null when the file carries no lyrics.
 */
object EmbeddedLyrics {

    private const val MAX_READ = 3 * 1024 * 1024

    fun read(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val head = readHead(stream, 12)
                when {
                    head.size >= 3 && head[0] == 'I'.code.toByte() &&
                        head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() -> readId3(context, uri)
                    head.size >= 4 && head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
                        head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> readFlac(stream)
                    head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> readMp4(context, uri)
                    else -> null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** InputStream.readNBytes is API 33+, so read defensively on all versions. */
    private fun readUpTo(stream: InputStream, count: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(count, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(remaining, buffer.size))
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun readHead(stream: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val result = stream.read(buffer, read, count - read)
            if (result <= 0) break
            read += result
        }
        return if (read == count) buffer else buffer.copyOf(read)
    }

    /* --------------------------------- ID3 -------------------------------- */

    private fun readId3(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readUpTo(it, MAX_READ) } ?: return null
        if (bytes.size < 10) return null
        val major = bytes[3].toInt() and 0xFF
        if (major !in 2..4) return null
        val size = syncSafe(bytes, 6)
        val end = minOf(bytes.size, 10 + size)
        var offset = 10

        val flags = bytes[5].toInt() and 0xFF
        if (flags and 0x40 != 0 && major >= 3) {
            offset += if (major == 4) syncSafe(bytes, offset) else readInt(bytes, offset) + 4
        }

        while (offset + 10 <= end) {
            val id = String(bytes, offset, 4, Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val frameSize = if (major == 4) syncSafe(bytes, offset + 4) else readInt(bytes, offset + 4)
            if (frameSize <= 0 || offset + 10 + frameSize > end) break
            val body = offset + 10

            if (id == "USLT" && frameSize > 4) {
                val text = decodeLyricsFrame(bytes, body, frameSize)
                if (!text.isNullOrBlank()) return text
            }
            if (id == "TXXX" && frameSize > 4) {
                val text = decodeUserText(bytes, body, frameSize)
                if (text != null && text.first.equals("LYRICS", true)) return text.second
            }
            offset += 10 + frameSize
        }
        null
    }.getOrNull()

    private fun decodeLyricsFrame(bytes: ByteArray, body: Int, size: Int): String? {
        val encoding = bytes[body].toInt() and 0xFF
        // encoding(1) + language(3) + descriptor(terminated) + text
        var index = body + 4
        val end = body + size
        val charset = charsetFor(encoding)
        if (encoding == 1 || encoding == 2) {
            while (index + 1 < end) {
                if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
                    index += 2
                    break
                }
                index += 2
            }
        } else {
            while (index < end && bytes[index] != 0.toByte()) index++
            index++
        }
        if (index >= end) return null
        return clean(String(bytes, index, end - index, charset))
    }

    private fun decodeUserText(bytes: ByteArray, body: Int, size: Int): Pair<String, String>? {
        val encoding = bytes[body].toInt() and 0xFF
        val charset = charsetFor(encoding)
        var index = body + 1
        val end = body + size
        val descEnd = if (encoding == 1 || encoding == 2) {
            var cursor = index
            while (cursor + 1 < end && !(bytes[cursor] == 0.toByte() && bytes[cursor + 1] == 0.toByte())) cursor += 2
            cursor
        } else {
            var cursor = index
            while (cursor < end && bytes[cursor] != 0.toByte()) cursor++
            cursor
        }
        val desc = String(bytes, index, descEnd - index, charset)
        index = if (encoding == 1 || encoding == 2) descEnd + 2 else descEnd + 1
        if (index >= end) return null
        return desc to clean(String(bytes, index, end - index, charset))
    }

    /* -------------------------------- FLAC -------------------------------- */

    private fun readFlac(stream: InputStream): String? {
        // Skip "fLaC"
        repeat(4) { stream.read() }
        while (true) {
            val header = stream.read()
            if (header < 0) return null
            val type = header and 0x7F
            val last = header and 0x80 != 0
            val length = readIntLe24(stream)
            if (length <= 0) return null
            if (type == 4) {
                val block = readUpTo(stream, length)
                return parseVorbisComment(block)
            }
            val skipped = stream.skip(length.toLong())
            if (skipped < length) return null
            if (last) return null
        }
    }

    private fun parseVorbisComment(block: ByteArray): String? {
        var offset = 0
        if (block.size < 8) return null
        val vendorLength = readIntLe(block, offset)
        offset += 4 + vendorLength
        if (offset + 4 > block.size) return null
        val count = readIntLe(block, offset)
        offset += 4
        repeat(count) {
            if (offset + 4 > block.size) return null
            val length = readIntLe(block, offset)
            offset += 4
            if (length <= 0 || offset + length > block.size) return null
            val entry = String(block, offset, length, Charsets.UTF_8)
            offset += length
            val key = entry.substringBefore('=')
            if (key.equals("LYRICS", true) || key.equals("UNSYNCEDLYRICS", true) || key.equals("UNSYNCED LYRICS", true)) {
                return clean(entry.substringAfter('='))
            }
        }
        return null
    }

    /* --------------------------------- MP4 -------------------------------- */

    private fun readMp4(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readUpTo(it, MAX_READ) } ?: return null
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var index = 0
        while (index < bytes.size - 8) {
            if (matchesAt(bytes, index, marker)) {
                // atom layout: [size][name:©lyr][data atom][version/flags][reserved][text]
                var cursor = index + 4
                val atomSize = readInt(bytes, cursor - 4)
                val bodyEnd = minOf(bytes.size, cursor - 4 + atomSize)
                cursor += 4
                if (cursor + 4 <= bytes.size && String(bytes, cursor, 4, Charsets.ISO_8859_1) == "data") {
                    cursor += 4
                    cursor += 8
                    if (cursor < bodyEnd) {
                        val text = String(bytes, cursor, bodyEnd - cursor, Charsets.UTF_8)
                        return clean(text)
                    }
                }
            }
            index++
        }
        null
    }.getOrNull()

    /* -------------------------------- utils ------------------------------- */

    private fun charsetFor(encoding: Int): Charset = when (encoding) {
        1, 2 -> Charsets.UTF_16
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun clean(raw: String): String = raw
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .trim()

    private fun matchesAt(bytes: ByteArray, index: Int, pattern: ByteArray): Boolean {
        if (index + pattern.size > bytes.size) return false
        for (i in pattern.indices) if (bytes[index + i] != pattern[i]) return false
        return true
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readIntLe24(stream: InputStream): Int {
        val b0 = stream.read()
        val b1 = stream.read()
        val b2 = stream.read()
        if (b2 < 0) return 0
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16)
    }
}
