package com.zeroplayer.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.components.MiniPlayer
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.nav.NavActions
import com.zeroplayer.app.ui.nav.Navigator
import com.zeroplayer.app.ui.screens.AlbumScreen
import com.zeroplayer.app.ui.screens.ArtistScreen
import com.zeroplayer.app.ui.screens.EqualizerScreen
import com.zeroplayer.app.ui.screens.FolderScreen
import com.zeroplayer.app.ui.screens.HomeScreen
import com.zeroplayer.app.ui.screens.LibraryScreen
import com.zeroplayer.app.ui.screens.LyricsScreen
import com.zeroplayer.app.ui.screens.NowPlayingScreen
import com.zeroplayer.app.ui.screens.PermissionScreen
import com.zeroplayer.app.ui.screens.PlaylistDetailScreen
import com.zeroplayer.app.ui.screens.PlaylistsScreen
import com.zeroplayer.app.ui.screens.QueueScreen
import com.zeroplayer.app.ui.screens.SearchScreen
import com.zeroplayer.app.ui.screens.SettingsScreen
import com.zeroplayer.app.ui.screens.SongListScreen
import com.zeroplayer.app.ui.theme.Zero
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
fun ZeroApp() {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = hasAudioPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(requiredPermissions())
    }

    LaunchedEffect(granted) {
        if (granted) Graph.music.refresh()
    }

    if (!granted) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(requiredPermissions()) },
            onOpenSettings = { openAppSettings(context) },
        )
        return
    }

    val library by Graph.music.state.collectAsStateWithLifecycle()
    val playback by PlaybackController.state.collectAsStateWithLifecycle()
    val navigator = remember { Navigator() }
    val current = navigator.current

    val navActions = remember(navigator) {
        NavActions(
            push = { navigator.push(it) },
            pop = { navigator.pop() },
            openTab = { navigator.openTab(it) },
            openNowPlaying = { navigator.push(Dest.NowPlaying) },
            playSongs = { songs, index, shuffle -> PlaybackController.playSongs(songs, index, shuffle) },
        )
    }

    BackHandler(enabled = navigator.stack.size > 1) {
        navigator.pop()
    }

    val bottomBarVisible = !current.isFullscreen
    val bottomInset = if (bottomBarVisible) 150.dp else 0.dp

    CompositionLocalProvider(
        LocalNav provides navActions,
        LocalBottomInset provides bottomInset,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Zero.Black)) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val enteringFullscreen = targetState.isFullscreen && !initialState.isFullscreen
                    val leavingFullscreen = initialState.isFullscreen && !targetState.isFullscreen
                    when {
                        enteringFullscreen -> (slideInVertically(tween(340)) { it } + fadeIn(tween(220)))
                            .togetherWith(fadeOut(tween(180)))
                        leavingFullscreen -> fadeIn(tween(200))
                            .togetherWith(slideOutVertically(tween(300)) { it } + fadeOut(tween(240)))
                        targetState.isTab || initialState.isTab ->
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 24 })
                                .togetherWith(fadeOut(tween(160)))
                        else -> (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(240)) { -it / 8 } + fadeOut(tween(160)))
                    }
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { dest ->
                when (dest) {
                    Dest.Home -> HomeScreen(library = library, playback = playback)
                    Dest.Library -> LibraryScreen(library = library)
                    Dest.Playlists -> PlaylistsScreen(library = library)
                    Dest.Search -> SearchScreen(library = library)
                    Dest.Settings -> SettingsScreen(library = library, playback = playback)
                    is Dest.Album -> AlbumScreen(albumId = dest.albumId, fallbackTitle = dest.title, library = library, playback = playback)
                    is Dest.Artist -> ArtistScreen(artistName = dest.name, library = library, playback = playback)
                    is Dest.Folder -> FolderScreen(path = dest.path, fallbackTitle = dest.title, library = library, playback = playback)
                    is Dest.PlaylistDetail -> PlaylistDetailScreen(playlistId = dest.id, fallbackTitle = dest.title, library = library, playback = playback)
                    is Dest.SongList -> SongListScreen(kind = dest.kind, title = dest.title, library = library, playback = playback)
                    Dest.NowPlaying -> NowPlayingScreen(playback = playback, library = library)
                    Dest.Queue -> QueueScreen(playback = playback)
                    Dest.Equalizer -> EqualizerScreen()
                    Dest.Lyrics -> LyricsScreen(playback = playback, library = library)
                }
            }

            if (bottomBarVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    MiniPlayer(
                        state = playback,
                        onOpen = { navigator.push(Dest.NowPlaying) },
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    ZeroBottomBar(
                        current = current,
                        onSelect = { tab ->
                            if (navigator.current == tab) navigator.openTab(tab) else navigator.openTab(tab)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZeroBottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    val tabs = listOf(
        Triple(Dest.Home, Icons.Rounded.Home, "Home"),
        Triple(Dest.Library, Icons.Rounded.LibraryMusic, "Library"),
        Triple(Dest.Playlists, Icons.Rounded.QueueMusic, "Playlists"),
        Triple(Dest.Search, Icons.Rounded.Search, "Search"),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Zero.Surface1.copy(alpha = 0.92f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (dest, icon, label) ->
            val selected = current === dest
            BottomBarItem(icon = icon, label = label, selected = selected) { onSelect(dest) }
        }
    }
}

@Composable
private fun BottomBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Zero.Chrome else Zero.TextTertiary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Zero.Silver else Zero.TextTertiary,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 14.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) Zero.Silver else Color.Transparent),
        )
    }
}

/* ----------------------------- permissions ----------------------------- */

fun hasAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

private fun openAppSettings(context: Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Unused helper kept for the equalizer shortcut on the tab bar. */
val EqualizerIcon: ImageVector = Icons.Rounded.GraphicEq
