package com.zeroplayer.app.ui.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every screen in ZERO PLAYER. */
sealed interface Dest {
    val key: String

    data object Home : Dest { override val key = "home" }
    data object Library : Dest { override val key = "library" }
    data object Playlists : Dest { override val key = "playlists" }
    data object Search : Dest { override val key = "search" }
    data object Settings : Dest { override val key = "settings" }

    data class Album(val albumId: Long, val title: String) : Dest {
        override val key get() = "album_$albumId"
    }

    data class Artist(val name: String) : Dest {
        override val key get() = "artist_$name"
    }

    data class Folder(val path: String, val title: String) : Dest {
        override val key get() = "folder_$path"
    }

    data class PlaylistDetail(val id: Long, val title: String) : Dest {
        override val key get() = "playlist_$id"
    }

    data class SongList(val kind: SongListKind, val title: String) : Dest {
        override val key get() = "list_${kind.name}"
    }

    data object NowPlaying : Dest { override val key = "now_playing" }
    data object Queue : Dest { override val key = "queue" }
    data object Equalizer : Dest { override val key = "equalizer" }
    data object Lyrics : Dest { override val key = "lyrics" }

    val isTab: Boolean
        get() = this is Home || this is Library || this is Playlists || this is Search

    val isFullscreen: Boolean
        get() = this is NowPlaying || this is Lyrics || this is Equalizer || this is Queue
}

enum class SongListKind { ALL, FAVORITES, RECENT, MOST_PLAYED }

/** Navigation callbacks shared with every screen. */
class NavActions(
    val push: (Dest) -> Unit,
    val pop: () -> Unit,
    val openTab: (Dest) -> Unit,
    val openNowPlaying: () -> Unit,
    val playSongs: (List<com.zeroplayer.app.data.model.Song>, Int, Boolean) -> Unit,
)

val LocalNav = staticCompositionLocalOf<NavActions> {
    error("NavActions not provided")
}

/** Bottom inset reserved for the mini player and tab bar. */
val LocalBottomInset = compositionLocalOf { 150.dp }

class Navigator {
    val stack = mutableStateListOf<Dest>(Dest.Home)
    val current: Dest get() = stack.last()

    fun push(dest: Dest) {
        if (stack.last() == dest) return
        stack.add(dest)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openTab(dest: Dest) {
        stack.clear()
        stack.add(dest)
    }

    fun replaceAll(vararg dests: Dest) {
        stack.clear()
        stack.addAll(dests)
    }
}

fun Dp.coerceAtLeastZero(): Dp = if (this < 0.dp) 0.dp else this
