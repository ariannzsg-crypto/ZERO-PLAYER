package com.zeroplayer.app.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Playlist covers are copied into the app's private files directory instead of
 * keeping the gallery URI. A picker URI can be revoked when the app restarts or
 * when the user clears the photo provider, which would silently blank the cover.
 */
object PlaylistCovers {

    private const val DIRECTORY = "playlist_covers"

    fun copyIntoStorage(context: Context, playlistId: Long, source: Uri): String? = runCatching {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, "playlist_$playlistId.img")
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        Uri.fromFile(target).toString()
    }.getOrNull()

    /** Removes a previously stored cover, never a file outside app storage. */
    fun release(context: Context, uri: String?) {
        val path = uri?.takeIf { it.startsWith("file://") }?.removePrefix("file://") ?: return
        val file = File(path)
        val ownedRoot = File(context.filesDir, DIRECTORY).absolutePath
        if (file.absolutePath.startsWith(ownedRoot)) file.delete()
    }
}
