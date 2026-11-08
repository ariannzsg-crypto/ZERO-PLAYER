package com.zeroplayer.app.lyrics

import android.content.Context
import android.net.Uri
import com.zeroplayer.app.data.Prefs
import com.zeroplayer.app.data.db.AppDatabase
import com.zeroplayer.app.data.db.LyricsEntity
import com.zeroplayer.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves lyrics for a track in priority order:
 * 1. manually imported LRC, 2. `.lrc` sidecar file, 3. embedded audio tags.
 * Imported lyrics are persisted in Room.
 */
class LyricsRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
) {

    private val cache = LinkedHashMap<Long, Lyrics>()

    suspend fun lyricsFor(song: Song): Lyrics = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }

        val stored = runCatching { db.lyrics().forSong(song.id) }.getOrNull()
        val resolved = when {
            stored != null -> LrcParser.parse(stored.raw, stored.source)
            else -> {
                val raw = readSidecar(song) ?: readEmbedded(song) ?: prefs.lyricsOverride(song.id)
                if (raw.isNullOrBlank()) Lyrics.EMPTY else LrcParser.parse(raw, "tags")
            }
        }
        if (resolved !== Lyrics.EMPTY) {
            if (cache.size > 12) cache.remove(cache.keys.first())
            cache[song.id] = resolved
        }
        resolved
    }

    private fun readSidecar(song: Song): String? {
        if (song.path.isBlank()) return null
        return runCatching {
            val audio = File(song.path)
            val base = audio.nameWithoutExtension
            val folder = audio.parentFile ?: return null
            val candidates = listOf("$base.lrc", "$base.LRC", "$base.txt")
            candidates.firstNotNullOfOrNull { name ->
                val file = File(folder, name)
                if (file.exists() && file.canRead()) file.readText() else null
            }
        }.getOrNull()
    }

    private fun readEmbedded(song: Song): String? = runCatching {
        EmbeddedLyrics.read(context, song.contentUri)
    }.getOrNull()

    suspend fun saveImportedLyrics(songId: Long, raw: String, source: String = "import"): Boolean =
        withContext(Dispatchers.IO) {
            if (raw.isBlank()) return@withContext false
            db.lyrics().save(
                LyricsEntity(
                    songId = songId,
                    raw = raw,
                    synced = LrcParser.parse(raw).synced,
                    source = source,
                    importedAt = System.currentTimeMillis(),
                ),
            )
            cache.remove(songId)
            true
        }

    suspend fun clearLyrics(songId: Long) = withContext(Dispatchers.IO) {
        db.lyrics().delete(songId)
        cache.remove(songId)
        prefs.clearLyricsOverride(songId)
    }

    suspend fun readUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText().take(512 * 1024)
            }
        }.getOrNull()
    }

    fun cachedLyrics(songId: Long): Lyrics? = cache[songId]
}
