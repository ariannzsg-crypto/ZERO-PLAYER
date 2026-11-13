package com.zeroplayer.app.net.online

import org.json.JSONObject
import java.net.URLEncoder

/**
 * YouTube through the official Data API for metadata and the official IFrame
 * player for playback.
 *
 * The data key lives on the backend, never in the APK, so search goes through
 * `/api/youtube/search`. Playback opens `YouTubePlayerScreen`, which embeds the
 * official player — ads, attribution and the no-background-playback policy are
 * whatever YouTube's player enforces.
 */
class YouTubeService(private val prefs: OnlinePrefs, private val http: OnlineHttp) {

    suspend fun search(query: String): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val backend = prefs.backendUrl
        require(backend.isNotBlank()) { "Set the backend URL first (Online Music → Setup)." }
        val url = "$backend/api/youtube/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=25"
        val response = http.request("GET", url)
        response.requireSuccess()
        val items = response.json().optJSONArray("items") ?: return emptyList()

        val tracks = mutableListOf<OnlineTrack>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            tracks += OnlineTrack(
                provider = OnlineProvider.YOUTUBE,
                id = id,
                uri = item.optString("url").ifBlank { "https://www.youtube.com/watch?v=$id" },
                title = item.optString("title"),
                subtitle = item.optString("channel"),
                artworkUrl = item.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
                durationMs = item.optLong("durationMs"),
                kind = OnlineKind.VIDEO,
                openUrl = "https://www.youtube.com/watch?v=$id",
            )
        }
        return tracks
    }

    /** Checks that the backend answers and knows its YouTube key. */
    suspend fun backendStatus(): BackendStatus {
        val backend = prefs.backendUrl
        require(backend.isNotBlank()) { "Set the backend URL first (Online Music → Setup)." }
        val response = http.request("GET", "$backend/api/health")
        response.requireSuccess()
        val json = response.json()
        return BackendStatus(
            reachable = true,
            youtube = json.optJSONObject("providers")?.optBoolean("youtube") == true,
            spotify = json.optJSONObject("providers")?.optBoolean("spotify") == true,
            soundcloud = json.optJSONObject("providers")?.optBoolean("soundcloud") == true,
        )
    }

    data class BackendStatus(
        val reachable: Boolean,
        val youtube: Boolean,
        val spotify: Boolean,
        val soundcloud: Boolean,
    )

    companion object {
        fun embedHtml(videoId: String): String = """
            <!DOCTYPE html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  html, body { margin: 0; padding: 0; background: #000; height: 100%; }
                  iframe { border: 0; width: 100%; height: 100%; display: block; }
                </style>
              </head>
              <body>
                <iframe
                  src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                  allow="autoplay; encrypted-media; picture-in-picture"
                  allowfullscreen></iframe>
              </body>
            </html>
        """.trimIndent()

        /** JSON echo used by the unit-free sanity check in the player screen. */
        fun attribution(videoId: String): String = JSONObject().put("videoId", videoId).toString()
    }
}
