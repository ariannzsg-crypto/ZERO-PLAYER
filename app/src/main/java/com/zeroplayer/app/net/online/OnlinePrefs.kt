package com.zeroplayer.app.net.online

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Token and configuration storage for the online section.
 *
 * Tokens live in a private preferences file that only this app can read. They
 * are never logged, never written to the library database and never leave the
 * device except as an `Authorization` header to the provider that issued them.
 */
class OnlinePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("zero_player_online", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKEND, value.trim().trimEnd('/')).apply()

    var spotifyClientId: String
        get() = prefs.getString(KEY_SPOTIFY_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SPOTIFY_ID, value.trim()).apply()

    var soundcloudClientId: String
        get() = prefs.getString(KEY_SOUNDCLOUD_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SOUNDCLOUD_ID, value.trim()).apply()

    var selectedProvider: String
        get() = prefs.getString(KEY_PROVIDER, OnlineProvider.SPOTIFY.key) ?: OnlineProvider.SPOTIFY.key
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    /* -------------------------------- tokens ------------------------------- */

    fun saveTokens(provider: OnlineProvider, access: String, refresh: String?, expiresIn: Long) {
        prefs.edit()
            .putString(accessKey(provider), access)
            .putString(refreshKey(provider), refresh)
            .putLong(expiryKey(provider), System.currentTimeMillis() + expiresIn * 1000L - 60_000L)
            .apply()
    }

    fun accessToken(provider: OnlineProvider): String = prefs.getString(accessKey(provider), "") ?: ""

    fun refreshToken(provider: OnlineProvider): String = prefs.getString(refreshKey(provider), "") ?: ""

    fun expiresAt(provider: OnlineProvider): Long = prefs.getLong(expiryKey(provider), 0L)

    fun hasSession(provider: OnlineProvider): Boolean =
        accessToken(provider).isNotBlank() || refreshToken(provider).isNotBlank()

    fun clearSession(provider: OnlineProvider) {
        prefs.edit()
            .remove(accessKey(provider))
            .remove(refreshKey(provider))
            .remove(expiryKey(provider))
            .remove(accountKey(provider))
            .apply()
    }

    fun isExpiring(provider: OnlineProvider): Boolean =
        expiresAt(provider) in 1..System.currentTimeMillis()

    var spotifyAccountName: String
        get() = prefs.getString(accountKey(OnlineProvider.SPOTIFY), "") ?: ""
        set(value) = prefs.edit().putString(accountKey(OnlineProvider.SPOTIFY), value).apply()

    var soundcloudAccountName: String
        get() = prefs.getString(accountKey(OnlineProvider.SOUNDCLOUD), "") ?: ""
        set(value) = prefs.edit().putString(accountKey(OnlineProvider.SOUNDCLOUD), value).apply()

    /* --------------------------- OAuth round trips -------------------------- */

    var oauthState: String
        get() = prefs.getString(KEY_STATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STATE, value).apply()

    var oauthVerifier: String
        get() = prefs.getString(KEY_VERIFIER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VERIFIER, value).apply()

    /* --------------------------- recently played --------------------------- */

    fun saveRecent(items: List<OnlineTrack>) {
        val array = JSONArray()
        items.take(20).forEach { track ->
            array.put(
                JSONObject().apply {
                    put("provider", track.provider.key)
                    put("id", track.id)
                    put("uri", track.uri)
                    put("title", track.title)
                    put("subtitle", track.subtitle)
                    put("artwork", track.artworkUrl ?: JSONObject.NULL)
                    put("duration", track.durationMs)
                    put("kind", track.kind.name)
                    put("openUrl", track.openUrl ?: JSONObject.NULL)
                },
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    fun loadRecent(): List<OnlineTrack> = runCatching {
        val array = JSONArray(prefs.getString(KEY_RECENT, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val provider = OnlineProvider.from(item.optString("provider")) ?: return@mapNotNull null
            OnlineTrack(
                provider = provider,
                id = item.optString("id"),
                uri = item.optString("uri"),
                title = item.optString("title"),
                subtitle = item.optString("subtitle"),
                artworkUrl = item.optString("artwork").takeIf { it.isNotBlank() && it != "null" },
                durationMs = item.optLong("duration"),
                kind = runCatching { OnlineKind.valueOf(item.optString("kind")) }
                    .getOrDefault(OnlineKind.TRACK),
                openUrl = item.optString("openUrl").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }.getOrDefault(emptyList())

    private fun accessKey(provider: OnlineProvider) = "token_${provider.key}"
    private fun refreshKey(provider: OnlineProvider) = "refresh_${provider.key}"
    private fun expiryKey(provider: OnlineProvider) = "expires_${provider.key}"
    private fun accountKey(provider: OnlineProvider) = "account_${provider.key}"

    companion object {
        private const val KEY_BACKEND = "backend_url"
        private const val KEY_SPOTIFY_ID = "spotify_client_id"
        private const val KEY_SOUNDCLOUD_ID = "soundcloud_client_id"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_STATE = "oauth_state"
        private const val KEY_VERIFIER = "oauth_verifier"
        private const val KEY_RECENT = "recent"
    }
}

/** PKCE helpers (RFC 7636) used by both Spotify and SoundCloud. */
object Pkce {

    fun randomVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun randomState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
