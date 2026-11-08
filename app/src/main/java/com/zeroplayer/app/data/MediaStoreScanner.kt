package com.zeroplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.zeroplayer.app.data.db.SongEntity
import com.zeroplayer.app.data.model.UNKNOWN_ALBUM
import com.zeroplayer.app.data.model.UNKNOWN_ARTIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the device audio library through MediaStore and converts it to rows
 * that can be cached in Room. Supports MP3, FLAC, WAV, AAC, OGG, M4A and OPUS.
 */
class MediaStoreScanner(private val context: Context) {

    suspend fun scan(): List<SongEntity> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.IS_MUSIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = ArrayList<SongEntity>(256)

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durationCol)
                    val fileName = cursor.getString(nameCol).orEmpty()
                    if (duration < 3_000L) continue
                    if (!isSupported(fileName)) continue

                    val dataPath = cursor.getString(dataCol).orEmpty()
                    val relative = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                    val folder = when {
                        relative.isNotBlank() -> "/storage/emulated/0/" + relative.trim('/')
                        dataPath.isNotBlank() -> File(dataPath).parent.orEmpty()
                        else -> ""
                    }

                    val rawTitle = cursor.getString(titleCol)
                    val title = when {
                        !rawTitle.isNullOrBlank() -> rawTitle
                        fileName.isNotBlank() -> fileName.substringBeforeLast('.')
                        else -> "Untitled"
                    }

                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: UNKNOWN_ARTIST
                    val album = cursor.getString(albumCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: UNKNOWN_ALBUM

                    result += SongEntity(
                        id = cursor.getLong(idCol),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = duration,
                        track = runCatching { cursor.getInt(trackCol) % 1000 }.getOrDefault(0),
                        year = cursor.getInt(yearCol),
                        path = dataPath,
                        folder = folder,
                        addedAt = cursor.getLong(addedCol) * 1000L,
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                    )
                }
            }
        }
        result
    }

    /** Resolves a playable content URI for a MediaStore audio id. */
    fun contentUri(id: Long) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "oga", "mka", "mp4")

        fun isSupported(fileName: String): Boolean {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return extension in SUPPORTED_EXTENSIONS
        }
    }
}
