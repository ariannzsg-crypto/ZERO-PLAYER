package com.zeroplayer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.net.online.OnlineKind
import com.zeroplayer.app.net.online.OnlineProvider
import com.zeroplayer.app.net.online.OnlineState
import com.zeroplayer.app.net.online.OnlineTrack
import com.zeroplayer.app.net.online.SpotifyNowPlaying
import com.zeroplayer.app.net.online.SpotifyService
import com.zeroplayer.app.ui.components.CoverArt
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.ZeroDivider
import com.zeroplayer.app.ui.formatDuration
import com.zeroplayer.app.ui.nav.Dest
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.delay

/* ---------------------------- provider identity ---------------------------- */

private val OnlineProvider.accent: Color
    get() = when (this) {
        OnlineProvider.SPOTIFY -> Color(0xFF1ED760)
        OnlineProvider.SOUNDCLOUD -> Color(0xFFFF6B2C)
        OnlineProvider.YOUTUBE -> Color(0xFFFF3B30)
    }

private val OnlineProvider.blurb: String
    get() = when (this) {
        OnlineProvider.SPOTIFY -> "Catalogue search · playback stays in Spotify"
        OnlineProvider.SOUNDCLOUD -> "Independent tracks · opens in SoundCloud"
        OnlineProvider.YOUTUBE -> "Videos · official embedded player"
    }

@Composable
fun OnlineMusicScreen() {
    val repository = Graph.online
    val state by repository.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val bottomInset = LocalBottomInset.current

    var setupOpen by remember { mutableStateOf(false) }
    var searchTick by remember { mutableStateOf(0) }

    fun openExternal(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // Spotify's player state is polled only while this screen is on screen.
    LaunchedEffect(state.provider, state.connected) {
        while (state.provider == OnlineProvider.SPOTIFY && state.connected) {
            repository.refreshNowPlaying()
            delay(5_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Zero.Black)) {
        AmbientGradient(accent = state.provider.accent)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 28.dp),
        ) {
            item { OnlineHeader(onSetup = { setupOpen = true }) }

            item {
                ProviderStrip(
                    state = state,
                    onSelect = { provider ->
                        repository.setProvider(provider)
                        if (provider == OnlineProvider.YOUTUBE) repository.reload()
                    },
                    onConnect = { provider ->
                        if (provider == OnlineProvider.YOUTUBE) {
                            repository.checkBackend()
                        } else {
                            runCatching { repository.authorizeUrl(provider) }
                                .onSuccess { url -> openExternal(url) }
                                .onFailure { message ->
                                    if (message.message?.contains("Setup") == true) setupOpen = true
                                }
                        }
                    },
                    onDisconnect = repository::disconnect,
                )
            }

            item {
                UnifiedSearchBar(
                    query = state.query,
                    accent = state.provider.accent,
                    onQueryChange = repository::updateQuery,
                    onSubmit = {
                        repository.search()
                        searchTick++
                    },
                )
            }

            if (state.loading) {
                items(5) { index -> ResultSkeleton(index) }
            }

            state.error?.let { message ->
                item {
                    ErrorCard(message = message) {
                        repository.clearError()
                        repository.search()
                    }
                }
            }

            state.notice?.let { message ->
                item {
                    NoticeCard(message) { repository.clearNotice() }
                }
            }

            if (state.provider == OnlineProvider.SPOTIFY && state.nowPlaying != null) {
                item {
                    SpotifyNowPlayingCard(
                        now = state.nowPlaying!!,
                        onTransport = repository::spotifyTransport,
                    )
                }
            }

            if (!state.loading && state.error == null && state.results.isEmpty()) {
                if (state.query.isBlank()) {
                    item { ProviderConnectCard(state) { setupOpen = true } }
                    if (state.recentlyPlayed.isNotEmpty()) {
                        item { SectionLabel("Recently played") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp)) {
                                items(state.recentlyPlayed, key = { it.uri }) { track ->
                                    RecentTile(track) {
                                        if (track.provider == OnlineProvider.YOUTUBE &&
                                            track.kind == OnlineKind.VIDEO
                                        ) {
                                            nav.push(Dest.OnlineVideo(track.id, track.title))
                                        } else {
                                            repository.play(track, ::openExternal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        EmptyState(
                            title = "Search ${state.provider.label}",
                            body = "Type a song, artist, album or playlist above. " +
                                "Results come from the official ${state.provider.label} API.",
                        )
                    }
                } else {
                    item {
                        EmptyState(
                            title = "Nothing found",
                            body = "No ${state.provider.label} result matched “${state.query}”. " +
                                "Try a shorter query.",
                        )
                    }
                }
            }

            itemsIndexed(state.results, key = { _, track -> "${track.provider.key}:${track.uri}" }) { index, track ->
                StaggeredRow(index = index, key = "${track.uri}:$searchTick") {
                    OnlineResultRow(
                        track = track,
                        accent = state.provider.accent,
                        onClick = {
                            when {
                                track.provider == OnlineProvider.YOUTUBE && track.kind == OnlineKind.VIDEO ->
                                    nav.push(Dest.OnlineVideo(track.id, track.title))
                                track.kind == OnlineKind.TRACK -> repository.play(track, ::openExternal)
                                else -> track.openUrl?.let(::openExternal)
                            }
                        },
                    )
                }
            }
        }
    }

    if (setupOpen) {
        SetupSheet(
            state = state,
            onDismiss = { setupOpen = false },
            onSave = { backend, spotify, soundcloud ->
                repository.saveSetup(backend, spotify, soundcloud)
                setupOpen = false
            },
            onCheck = { repository.checkBackend() },
        )
    }
}

/* --------------------------------- chrome --------------------------------- */

@Composable
private fun OnlineHeader(onSetup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel("Online")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Music",
                    style = MaterialTheme.typography.displayMedium,
                    color = Zero.TextPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSetup)
                    .background(Zero.Surface2.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, "Setup", tint = Zero.SilverDim, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Spotify, SoundCloud and YouTube in one place. Audio stays in each provider's own " +
                "player — the equalizer and visualizer remain local-file features.",
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextTertiary,
        )
    }
}

@Composable
private fun AmbientGradient(accent: Color) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val animatedAccent by animateColorAsState(accent, tween(650), label = "accent")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(animatedAccent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(90f + shift * 520f, 40f + shift * 160f),
                    radius = 900f,
                ),
            ),
    )
}

@Composable
private fun ProviderStrip(
    state: OnlineState,
    onSelect: (OnlineProvider) -> Unit,
    onConnect: (OnlineProvider) -> Unit,
    onDisconnect: (OnlineProvider) -> Unit,
) {
    AnimatedContent(
        targetState = state.provider,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.02f))
        },
        label = "provider-morph",
    ) { provider ->
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OnlineProvider.entries.forEach { entry ->
                    ProviderCard(
                        provider = entry,
                        selected = entry == provider,
                        connected = state.statuses[entry]?.connected == true,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(entry) },
                    )
                }
            }

            val status = state.statuses[provider]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (status?.connected == true) provider.accent else Zero.TextTertiary,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append(provider.blurb)
                        if (status?.accountName != null) append(" · ${status.accountName}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Zero.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (status?.connected == true && provider != OnlineProvider.YOUTUBE) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelSmall,
                        color = Zero.SilverDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onDisconnect(provider) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else if (provider != OnlineProvider.YOUTUBE && status?.connected != true) {
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelSmall,
                        color = provider.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onConnect(provider) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: OnlineProvider,
    selected: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1f else 0.955f, tween(240), label = "card-scale")
    val border by animateColorAsState(
        if (selected) provider.accent.copy(alpha = 0.85f) else Zero.Hairline,
        tween(240),
        label = "card-border",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "press")

    Column(
        modifier = modifier
            .scale(scale * pressScale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (selected) provider.accent.copy(alpha = 0.20f) else Zero.Surface2,
                        Zero.Surface0,
                    ),
                ),
            )
            .border(0.9.dp, border, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(provider.accent),
            )
            Spacer(Modifier.width(6.dp))
            if (connected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = provider.accent.copy(alpha = 0.9f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = provider.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Zero.Chrome else Zero.Silver,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        MicroLabel(if (connected) "Linked" else "Tap", color = Zero.TextTertiary)
    }
}

@Composable
private fun UnifiedSearchBar(
    query: String,
    accent: Color,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (focused.value) accent.copy(alpha = 0.7f) else Zero.Hairline,
        tween(200),
        label = "search-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zero.Surface1)
            .border(0.9.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (focused.value) accent else Zero.TextTertiary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Song, artist, album, playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zero.TextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Zero.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(Zero.Chrome),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused.value = it.isFocused },
            )
        }
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(tween(150)) + scaleIn(tween(150)),
            exit = fadeOut(tween(120)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = accent,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onSubmit),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Zero.Silver,
        modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 8.dp),
    )
}

/* --------------------------------- rows ----------------------------------- */

@Composable
private fun StaggeredRow(index: Int, key: String, content: @Composable () -> Unit) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(12) * 28L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 3 },
    ) {
        content()
    }
}

@Composable
private fun OnlineResultRow(track: OnlineTrack, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(100), label = "row-press")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            artworkUri = track.artworkUrl,
            modifier = Modifier.size(54.dp),
            corner = 12.dp,
            label = track.title,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Zero.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(track.subtitle.ifBlank { track.kind.name.lowercase() })
                    if (track.durationMs > 0) append(" · ${formatDuration(track.durationMs)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (track.kind == OnlineKind.TRACK) {
                Icons.Rounded.PlayArrow
            } else {
                Icons.AutoMirrored.Rounded.OpenInNew
            },
            contentDescription = null,
            tint = if (track.kind == OnlineKind.TRACK) accent else Zero.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RecentTile(track: OnlineTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        CoverArt(
            artworkUri = track.artworkUrl,
            modifier = Modifier.size(124.dp),
            corner = 16.dp,
            label = track.title,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.provider.label,
            style = MaterialTheme.typography.labelSmall,
            color = Zero.TextTertiary,
            maxLines = 1,
        )
    }
}

/* -------------------------------- states ---------------------------------- */

@Composable
private fun ResultSkeleton(index: Int) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = index * 90), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Zero.Surface3.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Zero.Surface3.copy(alpha = alpha)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Zero.Surface2.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zero.Danger.copy(alpha = 0.10f))
            .border(0.9.dp, Zero.Danger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.Silver,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Refresh, null, tint = Zero.Chrome, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelMedium,
                color = Zero.Chrome,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NoticeCard(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Zero.Surface2.copy(alpha = 0.8f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.Silver,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss",
            tint = Zero.TextTertiary,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun ProviderConnectCard(state: OnlineState, onSetup: () -> Unit) {
    val provider = state.provider
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Zero.Surface1.copy(alpha = 0.9f))
            .border(0.8.dp, Zero.Hairline, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(
            text = when (provider) {
                OnlineProvider.SPOTIFY -> "Connect Spotify"
                OnlineProvider.SOUNDCLOUD -> "Connect SoundCloud"
                OnlineProvider.YOUTUBE -> "YouTube is ready"
            },
            style = MaterialTheme.typography.titleSmall,
            color = Zero.Chrome,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (provider) {
                OnlineProvider.SPOTIFY ->
                    "Sign-in uses OAuth with PKCE. The client secret stays on your Vercel backend; " +
                        "the phone only holds a revocable token. Playback control needs Spotify Premium " +
                        "and an open Spotify device."
                OnlineProvider.SOUNDCLOUD ->
                    "SoundCloud needs an approved app for streaming. ZERO PLAYER searches the catalogue " +
                        "and hands tracks to the SoundCloud app — audio never enters this player."
                OnlineProvider.YOUTUBE ->
                    "Search runs through your backend, which holds the YouTube Data API key. Videos play " +
                        "in YouTube's official embedded player with its ads and attribution."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextTertiary,
        )
        if (provider != OnlineProvider.YOUTUBE) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.connected) "Connected" else "Set up credentials",
                    style = MaterialTheme.typography.labelMedium,
                    color = provider.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onSetup)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                if (state.connected) {
                    Text(
                        text = "Connect another account",
                        style = MaterialTheme.typography.labelSmall,
                        color = Zero.TextTertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Zero.Silver)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextTertiary,
        )
    }
}

/* --------------------------- Spotify now playing --------------------------- */

@Composable
private fun SpotifyNowPlayingCard(now: SpotifyNowPlaying, onTransport: (SpotifyService.Transport, Long) -> Unit) {
    var dragPosition by remember(now.title) { mutableStateOf<Float?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Zero.surfaceGradient)
            .border(0.8.dp, Zero.Hairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        MicroLabel("Playing on Spotify", color = OnlineProvider.SPOTIFY.accent)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(
                artworkUri = now.artworkUrl,
                modifier = Modifier.size(56.dp),
                corner = 12.dp,
                label = now.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = now.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zero.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = now.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = Zero.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = dragPosition ?: now.positionMs.toFloat().coerceIn(0f, now.durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onTransport(SpotifyService.Transport.SEEK, it.toLong()) }
                dragPosition = null
            },
            valueRange = 0f..now.durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = OnlineProvider.SPOTIFY.accent,
                activeTrackColor = OnlineProvider.SPOTIFY.accent,
                inactiveTrackColor = Zero.Hairline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(Icons.Rounded.SkipPrevious) { onTransport(SpotifyService.Transport.PREVIOUS, 0) }
            Spacer(Modifier.width(18.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(OnlineProvider.SPOTIFY.accent)
                    .clickable { onTransport(SpotifyService.Transport.PLAY_PAUSE, 0) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (now.isPlaying) "Pause" else "Play",
                    tint = Zero.Black,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            TransportButton(Icons.Rounded.SkipNext) { onTransport(SpotifyService.Transport.NEXT, 0) }
        }
    }
}

@Composable
private fun TransportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Zero.Silver, modifier = Modifier.size(22.dp))
    }
}

/* ------------------------------- setup sheet ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSheet(
    state: OnlineState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onCheck: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var backend by remember { mutableStateOf(state.backendUrl) }
    var spotifyId by remember { mutableStateOf(state.spotifyClientId) }
    var soundcloudId by remember { mutableStateOf(state.soundcloudClientId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Zero.Surface1,
        contentColor = Zero.TextPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            MicroLabel("Online setup")
            Spacer(Modifier.height(6.dp))
            Text("Credentials", style = MaterialTheme.typography.titleLarge, color = Zero.Chrome)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The client secret and the YouTube key live only on your Vercel backend. " +
                    "The app stores the backend URL and the public client IDs.",
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary,
            )
            Spacer(Modifier.height(16.dp))

            SetupField("Backend URL", backend, "https://your-project.vercel.app") { backend = it }
            Spacer(Modifier.height(12.dp))
            SetupField("Spotify client ID", spotifyId, "from developer.spotify.com/dashboard") { spotifyId = it }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Redirect URI: ${SpotifyService.REDIRECT_URI}",
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
            SetupField("SoundCloud client ID", soundcloudId, "from developers.soundcloud.com") { soundcloudId = it }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Redirect URI: \$backend/api/soundcloud/callback (HTTPS only)",
                style = MaterialTheme.typography.labelSmall,
                color = Zero.TextTertiary,
            )

            Spacer(Modifier.height(18.dp))
            ZeroDivider()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Check backend",
                    style = MaterialTheme.typography.labelMedium,
                    color = Zero.Silver,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCheck)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Zero.chromeGradient)
                        .clickable { onSave(backend, spotifyId, soundcloudId) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge,
                        color = Zero.Black,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SetupField(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Zero.Surface2.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MicroLabel(label, color = Zero.TextTertiary)
        Spacer(Modifier.height(4.dp))
        Box {
            if (value.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Zero.TextTertiary.copy(alpha = 0.6f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Zero.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(Zero.Chrome),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
