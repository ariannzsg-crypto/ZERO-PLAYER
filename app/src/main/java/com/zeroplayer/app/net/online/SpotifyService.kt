package com.zeroplayer.app.net.online

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Spotify through the official Web API.
 *
 * Why not the Spotify Android (App Remote) SDK?
 *  * Spotify ships it as a beta AAR that is not on Maven Central, so it would
 *    need a hand-managed binary in the repository.
 *  * The Web API already covers catalogue search plus, for a Spotify Premium
 *    account with an active device, play/pause/next/previous/seek.
 *
 * Audio always stays inside the Spotify app: ZERO PLAYER never receives, decodes
 * or downloads Spotify audio, so the equalizer and visualizer do not apply here.
 */
class SpotifyService(
    private val prefs: OnlinePrefs,
    private val http: OnlineHttp,
) {

    enum class Transport { PLAY_PAUSE, NEXT, PREVIOUS, SEEK }

    /** Builds the authorize URL. PKCE means no client secret touches the phone. */
    fun authorizeUrl(): String {
        val clientId = prefs.spotifyClientId
        require(clientId.isNotBlank()) { "Add your Spotify client ID in Online Music → Setup first." }
        val verifier = Pkce.randomVerifier()
        val state = Pkce.randomState()
        prefs.oauthVerifier = verifier
        prefs.oauthState = state
        val params = buildMap {
            put("client_id", clientId)
            put("response_type", "code")
            put("redirect_uri", REDIRECT_URI)
            put("code_challenge_method", "S256")
            put("code_challenge", Pkce.challenge(verifier))
            put("state", state)
            put("scope", SCOPES.joinToString(" "))
        }
        return AUTHORIZE_URL + "?" + params.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
    }

    /** Exchanges the authorization code through the backend (which holds the secret). */
    suspend fun completeAuth(code: String): Result<Unit> = runCatching {
        val backend = prefs.backendUrl
        require(backend.isNotBlank()) { "Set the backend URL first (Online Music → Setup)." }
        val body = JSONObject().apply {
            put("grantType", "authorization_code")
            put("code", code)
            put("codeVerifier", prefs.oauthVerifier)
            put("redirectUri", REDIRECT_URI)
        }
        val response = http.request(
            "POST",
            "$backend/api/spotify/token",
            body = body.toString(),
        )
        response.requireSuccess()
        val json = response.json()
        prefs.saveTokens(
            OnlineProvider.SPOTIFY,
            access = json.optString("accessToken"),
            refresh = json.optString("refreshToken").takeIf { it.isNotBlank() },
            expiresIn = json.optLong("expiresIn", 3600),
        )
        prefs.oauthVerifier = ""
        prefs.oauthState = ""
    }

    /** Returns a usable access token, refreshing it when it is about to expire. */
    suspend fun token(): String {
        val existing = prefs.accessToken(OnlineProvider.SPOTIFY)
        if (existing.isNotBlank() && !prefs.isExpiring(OnlineProvider.SPOTIFY)) return existing

        val refresh = prefs.refreshToken(OnlineProvider.SPOTIFY)
        if (refresh.isBlank()) {
            if (existing.isNotBlank()) return existing
            throw IllegalStateException("Spotify is not connected.")
        }
        val backend = prefs.backendUrl
        if (backend.isBlank()) throw IllegalStateException("Set the backend URL first (Online Music → Setup).")
        val response = http.request(
            "POST",
            "$backend/api/spotify/token",
            body = JSONObject().apply {
                put("grantType", "refresh_token")
                put("refreshToken", refresh)
            }.toString(),
        )
        response.requireSuccess()
        val json = response.json()
        prefs.saveTokens(
            OnlineProvider.SPOTIFY,
            access = json.optString("accessToken"),
            refresh = json.optString("refreshToken").takeIf { it.isNotBlank() } ?: refresh,
            expiresIn = json.optLong("expiresIn", 3600),
        )
        return json.optString("accessToken")
    }

    /** The signed-in Spotify display name, used as the connection label. */
    suspend fun fetchAccountName(): String? {
        val response = http.request("GET", "$API/me", headers = auth(token()))
        val json = response.obj() ?: return null
        val name = json.optString("display_name").ifBlank { json.optString("id") }
        if (name.isNotBlank()) prefs.spotifyAccountName = name
        return name.ifBlank { null }
    }

    suspend fun search(query: String): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val url = "$API/search?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&type=track,album,artist,playlist&limit=10"
        val json = http.request("GET", url, headers = auth(token())).also { it.requireSuccess() }.json()
        val items = mutableListOf<OnlineTrack>()

        json.optJSONObject("tracks")?.optJSONArray("items")?.let { array ->
            for (index in 0 until array.length()) {
                val track = array.optJSONObject(index) ?: continue
                items += OnlineTrack(
                    provider = OnlineProvider.SPOTIFY,
                    id = track.optString("id"),
                    uri = track.optString("uri"),
                    title = track.optString("name"),
                    subtitle = artistsOf(track),
                    artworkUrl = imageOf(track),
                    durationMs = track.optLong("duration_ms"),
                    kind = OnlineKind.TRACK,
                    openUrl = "https://open.spotify.com/track/${track.optString("id")}",
                )
            }
        }
        json.optJSONObject("albums")?.optJSONArray("items")?.let { array ->
            for (index in 0 until array.length()) {
                val album = array.optJSONObject(index) ?: continue
                items += OnlineTrack(
                    provider = OnlineProvider.SPOTIFY,
                    id = album.optString("id"),
                    uri = album.optString("uri"),
                    title = album.optString("name"),
                    subtitle = "Album · " + artistsOf(album),
                    artworkUrl = imageOf(album),
                    durationMs = 0,
                    kind = OnlineKind.ALBUM,
                    openUrl = "https://open.spotify.com/album/${album.optString("id")}",
                )
            }
        }
        json.optJSONObject("artists")?.optJSONArray("items")?.let { array ->
            for (index in 0 until array.length()) {
                val artist = array.optJSONObject(index) ?: continue
                items += OnlineTrack(
                    provider = OnlineProvider.SPOTIFY,
                    id = artist.optString("id"),
                    uri = artist.optString("uri"),
                    title = artist.optString("name"),
                    subtitle = "Artist",
                    artworkUrl = imageOf(artist),
                    durationMs = 0,
                    kind = OnlineKind.ARTIST,
                    openUrl = "https://open.spotify.com/artist/${artist.optString("id")}",
                )
            }
        }
        json.optJSONObject("playlists")?.optJSONArray("items")?.let { array ->
            for (index in 0 until array.length()) {
                val playlist = array.optJSONObject(index) ?: continue
                val count = playlist.optJSONObject("tracks")?.optInt("total") ?: 0
                items += OnlineTrack(
                    provider = OnlineProvider.SPOTIFY,
                    id = playlist.optString("id"),
                    uri = playlist.optString("uri"),
                    title = playlist.optString("name"),
                    subtitle = "Playlist · $count tracks",
                    artworkUrl = imageOf(playlist),
                    durationMs = 0,
                    kind = OnlineKind.PLAYLIST,
                    openUrl = "https://open.spotify.com/playlist/${playlist.optString("id")}",
                )
            }
        }
        return items
    }

    suspend fun nowPlaying(): SpotifyNowPlaying? {
        val response = http.request("GET", "$API/me/player", headers = auth(token()))
        if (response.code == 204) return null
        val json = response.obj() ?: return null
        val item = json.optJSONObject("item") ?: return null
        return SpotifyNowPlaying(
            title = item.optString("name"),
            artist = artistsOf(item),
            artworkUrl = imageOf(item),
            isPlaying = json.optBoolean("is_playing"),
            positionMs = json.optLong("progress_ms"),
            durationMs = item.optLong("duration_ms"),
        )
    }

    /**
     * Sends a transport command. Spotify only accepts these for a Premium
     * account with an active device; anything else is reported verbatim.
     */
    suspend fun transport(action: Transport, uri: String? = null, positionMs: Long = 0L) {
        val token = token()
        val response = when (action) {
            Transport.PLAY_PAUSE -> {
                val current = nowPlaying()
                if (current?.isPlaying == true) {
                    http.request("PUT", "$API/me/player/pause", headers = auth(token))
                } else {
                    val body = uri?.let {
                        JSONObject().put("uris", listOf(it)).toString()
                    }
                    http.request("PUT", "$API/me/player/play", body = body, headers = auth(token))
                }
            }
            Transport.NEXT -> http.request("POST", "$API/me/player/next", headers = auth(token))
            Transport.PREVIOUS -> http.request("POST", "$API/me/player/previous", headers = auth(token))
            Transport.SEEK -> http.request(
                "PUT",
                "$API/me/player/seek?position_ms=$positionMs",
                headers = auth(token),
            )
        }
        if (response.code !in 200..299) {
            response.requireSuccess()
        }
    }

    suspend fun playTrack(uri: String) {
        val token = token()
        val response = http.request(
            "PUT",
            "$API/me/player/play",
            body = JSONObject().put("uris", listOf(uri)).toString(),
            headers = auth(token),
        )
        if (!response.isSuccessful) response.requireSuccess()
    }

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun artistsOf(node: JSONObject): String {
        val artists = node.optJSONArray("artists") ?: return ""
        return (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun imageOf(node: JSONObject): String? {
        val images = node.optJSONArray("images")
            ?: node.optJSONObject("album")?.optJSONArray("images")
            ?: return null
        return if (images.length() > 0) images.optJSONObject(0)?.optString("url") else null
    }

    companion object {
        const val REDIRECT_URI = "zeroplayer://oauth/spotify"
        private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val API = "https://api.spotify.com/v1"
        private val SCOPES = listOf(
            "user-read-private",
            "user-read-email",
            "playlist-read-private",
            "user-library-read",
            "user-read-playback-state",
            "user-modify-playback-state",
        )
    }
}
