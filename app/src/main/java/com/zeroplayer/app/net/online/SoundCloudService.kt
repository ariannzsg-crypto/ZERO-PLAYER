package com.zeroplayer.app.net.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * SoundCloud through its official API.
 *
 * SoundCloud requires a registered application, and streaming is only available
 * to approved apps. Because of that the app never guesses a stream URL: tracks
 * are browsed here and opened in the SoundCloud app or website, where SoundCloud
 * itself decides what the listener is allowed to hear. Nothing is scraped and no
 * audio is downloaded.
 */
class SoundCloudService(
    private val prefs: OnlinePrefs,
    private val http: OnlineHttp,
) {

    /**
     * SoundCloud only accepts an HTTPS redirect URI, so the backend callback
     * receives the code and forwards it to `zeroplayer://oauth/soundcloud`.
     */
    fun authorizeUrl(): String {
        val clientId = prefs.soundcloudClientId
        require(clientId.isNotBlank()) { "Add your SoundCloud client ID in Online Music → Setup first." }
        val backend = prefs.backendUrl
        require(backend.isNotBlank()) { "Set the backend URL first (Online Music → Setup)." }
        val verifier = Pkce.randomVerifier()
        val state = Pkce.randomState()
        prefs.oauthVerifier = verifier
        prefs.oauthState = state
        val params = buildMap {
            put("client_id", clientId)
            put("redirect_uri", "$backend/api/soundcloud/callback")
            put("response_type", "code")
            put("code_challenge", Pkce.challenge(verifier))
            put("code_challenge_method", "S256")
            put("state", state)
        }
        return AUTHORIZE_URL + "?" + params.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
    }

    suspend fun token(): String {
        val existing = prefs.accessToken(OnlineProvider.SOUNDCLOUD)
        if (existing.isNotBlank() && !prefs.isExpiring(OnlineProvider.SOUNDCLOUD)) return existing

        val refresh = prefs.refreshToken(OnlineProvider.SOUNDCLOUD)
        if (refresh.isBlank()) {
            if (existing.isNotBlank()) return existing
            throw IllegalStateException("SoundCloud is not connected.")
        }
        val backend = prefs.backendUrl
        if (backend.isBlank()) throw IllegalStateException("Set the backend URL first (Online Music → Setup).")
        val response = http.request(
            "POST",
            "$backend/api/soundcloud/token",
            body = JSONObject().put("refreshToken", refresh).toString(),
        )
        response.requireSuccess()
        val json = response.json()
        prefs.saveTokens(
            OnlineProvider.SOUNDCLOUD,
            access = json.optString("accessToken"),
            refresh = json.optString("refreshToken").takeIf { it.isNotBlank() } ?: refresh,
            expiresIn = json.optLong("expiresIn", 3600),
        )
        return json.optString("accessToken")
    }

    suspend fun search(query: String): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val url = "$API/tracks?q=${URLEncoder.encode(query, "UTF-8")}&limit=25&linked_partitioning=false"
        val token = token()

        // SoundCloud's v2 API documents `OAuth <token>`; some deployments still
        // expect a bearer token, so both are tried before giving up.
        var response = http.request("GET", url, headers = mapOf("Authorization" to "OAuth $token"))
        if (response.code == 401) {
            response = http.request("GET", url, headers = mapOf("Authorization" to "Bearer $token"))
        }
        if (response.code == 401 && prefs.accessToken(OnlineProvider.SOUNDCLOUD).isNotBlank()) {
            // The stored token may have been revoked on the SoundCloud side.
            prefs.clearSession(OnlineProvider.SOUNDCLOUD)
        }
        response.requireSuccess()

        val collection = runCatching { JSONArray(response.body) }.getOrNull()
            ?: response.json().optJSONArray("collection")
            ?: JSONArray()
        val items = mutableListOf<OnlineTrack>()
        for (index in 0 until collection.length()) {
            val track = collection.optJSONObject(index) ?: continue
            val permalink = track.optString("permalink_url")
            items += OnlineTrack(
                provider = OnlineProvider.SOUNDCLOUD,
                id = track.optString("id"),
                uri = permalink,
                title = track.optString("title"),
                subtitle = track.optJSONObject("user")?.optString("username").orEmpty(),
                artworkUrl = track.optString("artwork_url")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.replace("-large", "-t500x500"),
                durationMs = track.optLong("duration"),
                kind = OnlineKind.TRACK,
                openUrl = permalink,
            )
        }
        return items
    }

    companion object {
        private const val AUTHORIZE_URL = "https://secure.soundcloud.com/authorize"
        private const val API = "https://api.soundcloud.com"
    }
}
