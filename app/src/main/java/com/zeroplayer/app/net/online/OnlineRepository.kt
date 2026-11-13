package com.zeroplayer.app.net.online

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single entry point for the Online Music section.
 *
 * Everything is a plain suspend call plus one `StateFlow`, matching how the rest
 * of the app talks to MediaStore and playback. Providers stay in their own
 * services; this class only orchestrates them.
 */
class OnlineRepository(private val prefs: OnlinePrefs) {

    private val http = OnlineHttp()
    private val spotify = SpotifyService(prefs, http)
    private val soundcloud = SoundCloudService(prefs, http)
    private val youtube = YouTubeService(prefs, http)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(OnlineState())
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update {
            it.copy(
                provider = OnlineProvider.from(prefs.selectedProvider) ?: OnlineProvider.SPOTIFY,
                statuses = OnlineProvider.entries.associateWith { provider ->
                    ProviderStatus(
                        provider = provider,
                        connected = prefs.hasSession(provider),
                        accountName = when (provider) {
                            OnlineProvider.SPOTIFY -> prefs.spotifyAccountName.takeIf { n -> n.isNotBlank() }
                            OnlineProvider.SOUNDCLOUD -> prefs.soundcloudAccountName.takeIf { n -> n.isNotBlank() }
                            OnlineProvider.YOUTUBE -> null
                        },
                        expiresAt = prefs.expiresAt(provider),
                    )
                },
                recentlyPlayed = prefs.loadRecent(),
                backendUrl = prefs.backendUrl,
                spotifyClientId = prefs.spotifyClientId,
                soundcloudClientId = prefs.soundcloudClientId,
            )
        }
    }

    fun setProvider(provider: OnlineProvider) {
        prefs.selectedProvider = provider.key
        _state.update { it.copy(provider = provider, error = null, results = emptyList(), query = "") }
        if (prefs.hasSession(provider)) {
            scope.launch { refreshProvider(provider) }
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /* -------------------------------- setup -------------------------------- */

    fun saveSetup(backendUrl: String, spotifyClientId: String, soundcloudClientId: String) {
        prefs.backendUrl = backendUrl
        prefs.spotifyClientId = spotifyClientId
        prefs.soundcloudClientId = soundcloudClientId
        _state.update {
            it.copy(
                backendUrl = prefs.backendUrl,
                spotifyClientId = prefs.spotifyClientId,
                soundcloudClientId = prefs.soundcloudClientId,
                notice = "Setup saved.",
            )
        }
    }

    /** Asks the backend which provider keys it holds and reports the result. */
    fun checkBackend() {
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { withContext(Dispatchers.IO) { youtube.backendStatus() } }
                .onSuccess { status ->
                    val missing = buildList {
                        if (!status.spotify) add("Spotify")
                        if (!status.soundcloud) add("SoundCloud")
                        if (!status.youtube) add("YouTube")
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            notice = if (missing.isEmpty()) {
                                "Backend reachable and all provider keys are set."
                            } else {
                                "Backend reachable. Missing keys: ${missing.joinToString(", ")}."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "The backend did not answer.",
                        )
                    }
                }
        }
    }

    /* ------------------------------- auth flow ----------------------------- */

    /** Returns the URL that must be opened in the browser app. */
    fun authorizeUrl(provider: OnlineProvider): String = when (provider) {
        OnlineProvider.SPOTIFY -> spotify.authorizeUrl()
        OnlineProvider.SOUNDCLOUD -> soundcloud.authorizeUrl()
        OnlineProvider.YOUTUBE -> throw IllegalStateException("YouTube needs no account; only the backend key.")
    }

    /** Called from `MainActivity` when `zeroplayer://oauth/...` comes back. */
    fun handleRedirect(uri: Uri) {
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { completeRedirect(uri) }
            result.onSuccess { (provider, name) ->
                _state.update {
                    it.copy(
                        loading = false,
                        notice = "${provider.label} connected${name?.let { n -> " as $n" } ?: ""}.",
                    )
                }
                reload()
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "Sign-in failed.") }
            }
        }
    }

    private suspend fun completeRedirect(uri: Uri): Pair<OnlineProvider, String?> {
        val hostOrPath = (uri.host.orEmpty() + uri.path.orEmpty()).lowercase()
        return when {
            "spotify" in hostOrPath -> {
                val state = uri.getQueryParameter("state")
                check(state != null && state == prefs.oauthState) { "Sign-in state did not match. Try again." }
                val error = uri.getQueryParameter("error")
                check(error == null) { "Spotify declined the sign-in: $error" }
                val code = uri.getQueryParameter("code")
                check(!code.isNullOrBlank()) { "Spotify did not return an authorization code." }
                spotify.completeAuth(code).getOrThrow()
                OnlineProvider.SPOTIFY to spotify.fetchAccountName()
            }
            "soundcloud" in hostOrPath -> {
                val state = uri.getQueryParameter("state")
                check(state != null && state == prefs.oauthState) { "Sign-in state did not match. Try again." }
                val error = uri.getQueryParameter("error")
                check(error == null) { "SoundCloud declined the sign-in: $error" }
                val accessToken = uri.getQueryParameter("access_token")
                check(!accessToken.isNullOrBlank()) { "SoundCloud did not return a token." }
                prefs.saveTokens(
                    OnlineProvider.SOUNDCLOUD,
                    access = accessToken,
                    refresh = uri.getQueryParameter("refresh_token"),
                    expiresIn = uri.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600L,
                )
                prefs.oauthVerifier = ""
                prefs.oauthState = ""
                OnlineProvider.SOUNDCLOUD to prefs.soundcloudAccountName.takeIf { it.isNotBlank() }
            }
            else -> throw IllegalArgumentException("Unknown sign-in redirect.")
        }
    }

    fun disconnect(provider: OnlineProvider) {
        prefs.clearSession(provider)
        _state.update {
            it.copy(
                results = it.results.filterNot { track -> track.provider == provider },
                nowPlaying = if (provider == OnlineProvider.SPOTIFY) null else it.nowPlaying,
                notice = "${provider.label} disconnected.",
            )
        }
        reload()
    }

    suspend fun refreshProvider(provider: OnlineProvider) {
        when (provider) {
            OnlineProvider.SPOTIFY -> runCatching { spotify.fetchAccountName() }.onSuccess { name ->
                if (name != null) {
                    _state.update {
                        it.copy(
                            statuses = it.statuses + (provider to it.status.copy(accountName = name)),
                        )
                    }
                }
            }
            OnlineProvider.SOUNDCLOUD -> Unit
            OnlineProvider.YOUTUBE -> checkBackend()
        }
    }

    /* ------------------------------- searching ----------------------------- */

    fun search() {
        val snapshot = _state.value
        val query = snapshot.query.trim()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), error = null) }
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                when (snapshot.provider) {
                    OnlineProvider.SPOTIFY -> spotify.search(query)
                    OnlineProvider.SOUNDCLOUD -> soundcloud.search(query)
                    OnlineProvider.YOUTUBE -> youtube.search(query)
                }
            }
            result.onSuccess { tracks ->
                _state.update { it.copy(loading = false, results = tracks, error = null) }
            }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, results = emptyList(), error = error.message ?: "Search failed.")
                }
            }
        }
    }

    /* ------------------------------- playback ------------------------------ */

    /**
     * Starts or opens a result. Spotify tracks are started through the Web API
     * (Premium + an active device); everything else is handed to the provider's
     * own app, because their audio never enters ZERO PLAYER's pipeline.
     */
    fun play(track: OnlineTrack, onExternal: (String) -> Unit) {
        rememberRecent(track)
        if (track.provider == OnlineProvider.SPOTIFY && track.kind == OnlineKind.TRACK) {
            scope.launch {
                runCatching { spotify.playTrack(track.uri) }
                    .onSuccess {
                        _state.update { it.copy(notice = "Playing on Spotify.", error = null) }
                        refreshNowPlaying()
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                error = "Spotify did not start playback: ${error.message}. " +
                                    "Spotify Premium and an open Spotify device are required.",
                            )
                        }
                        track.openUrl?.let(onExternal)
                    }
            }
        } else {
            track.openUrl?.let(onExternal)
        }
    }

    fun spotifyTransport(action: SpotifyService.Transport, positionMs: Long = 0L) {
        scope.launch {
            runCatching { spotify.transport(action, positionMs = positionMs) }
                .onSuccess { refreshNowPlaying() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Spotify rejected the command.") }
                }
        }
    }

    suspend fun refreshNowPlaying() {
        if (!prefs.hasSession(OnlineProvider.SPOTIFY)) return
        runCatching { spotify.nowPlaying() }.onSuccess { now ->
            _state.update { it.copy(nowPlaying = now) }
        }
    }

    private fun rememberRecent(track: OnlineTrack) {
        val current = _state.value.recentlyPlayed
        val updated = (listOf(track) + current.filterNot { it.uri == track.uri }).take(20)
        prefs.saveRecent(updated)
        _state.update { it.copy(recentlyPlayed = updated) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
