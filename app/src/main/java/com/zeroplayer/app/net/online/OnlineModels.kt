package com.zeroplayer.app.net.online

/** The three external catalogues ZERO PLAYER can browse. */
enum class OnlineProvider(val key: String, val label: String) {
    SPOTIFY("spotify", "Spotify"),
    SOUNDCLOUD("soundcloud", "SoundCloud"),
    YOUTUBE("youtube", "YouTube"),
    ;

    companion object {
        fun from(key: String?): OnlineProvider? = entries.firstOrNull { it.key == key }
    }
}

enum class OnlineKind { TRACK, ALBUM, ARTIST, PLAYLIST, VIDEO }

/**
 * One row in the unified result list. Every provider is normalised into this
 * shape so the UI does not need to know which API answered.
 */
data class OnlineTrack(
    val provider: OnlineProvider,
    val id: String,
    /** Spotify URI / SoundCloud permalink / YouTube watch URL. */
    val uri: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val kind: OnlineKind = OnlineKind.TRACK,
    /** Where tapping the row sends the user inside the provider's own app/site. */
    val openUrl: String? = null,
)

/** Spotify's live player state, read from the Web API (never from ExoPlayer). */
data class SpotifyNowPlaying(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/** Result of the last `/api/health` probe, shown inline in the diagnostics panel. */
data class BackendHealth(
    val reachable: Boolean,
    val spotify: Boolean,
    val soundcloud: Boolean,
    val youtube: Boolean,
    val checkedAt: Long,
    val detail: String? = null,
)

data class ProviderStatus(
    val provider: OnlineProvider,
    val connected: Boolean = false,
    val accountName: String? = null,
    val expiresAt: Long = 0L,
)

/**
 * What the Online Music section renders. Kept immutable so Compose recomposes
 * only when something that matters actually changes.
 */
data class OnlineState(
    val provider: OnlineProvider = OnlineProvider.SPOTIFY,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<OnlineTrack> = emptyList(),
    val statuses: Map<OnlineProvider, ProviderStatus> = emptyMap(),
    val nowPlaying: SpotifyNowPlaying? = null,
    val recentlyPlayed: List<OnlineTrack> = emptyList(),
    val backendUrl: String = "",
    val spotifyClientId: String = "",
    val soundcloudClientId: String = "",
    val notice: String? = null,
    val backendHealth: BackendHealth? = null,
) {
    val status: ProviderStatus get() = statuses[provider] ?: ProviderStatus(provider)
    val connected: Boolean get() = status.connected
}
