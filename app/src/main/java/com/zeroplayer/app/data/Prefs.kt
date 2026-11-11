package com.zeroplayer.app.data

import android.content.Context
import com.zeroplayer.app.data.model.SmartKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small SharedPreferences facade for state that is not relational:
 * the last queue, playback position and equalizer settings.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("zero_player_prefs", Context.MODE_PRIVATE)

    var lastQueueIds: List<Long>
        get() = prefs.getString(KEY_QUEUE, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()
        set(value) {
            prefs.edit().putString(KEY_QUEUE, value.joinToString(",")).apply()
        }

    var lastQueueIndex: Int
        get() = prefs.getInt(KEY_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_INDEX, value).apply()

    var lastPositionMs: Long
        get() = prefs.getLong(KEY_POSITION, 0L)
        set(value) = prefs.edit().putLong(KEY_POSITION, value).apply()

    var lastSongId: Long
        get() = prefs.getLong(KEY_SONG, -1L)
        set(value) = prefs.edit().putLong(KEY_SONG, value).apply()

    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    var shuffleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    var repeatMode: Int
        get() = prefs.getInt(KEY_REPEAT, 0)
        set(value) = prefs.edit().putInt(KEY_REPEAT, value).apply()

    var librarySort: String
        get() = prefs.getString(KEY_SORT, "TITLE") ?: "TITLE"
        set(value) = prefs.edit().putString(KEY_SORT, value).apply()

    var sleepTimerEndAt: Long
        get() = prefs.getLong(KEY_SLEEP, 0L)
        set(value) = prefs.edit().putLong(KEY_SLEEP, value).apply()

    var crossfadeMs: Int
        get() = prefs.getInt(KEY_CROSSFADE, 0)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE, value).apply()

    /* ----------------------------- equalizer ----------------------------- */

    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ON, value).apply()

    var eqGains: FloatArray
        get() {
            val encoded = prefs.getString(KEY_EQ_GAINS, null) ?: return FloatArray(DEFAULT_BANDS.size)
            return encoded.split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()
                .takeIf { it.isNotEmpty() } ?: FloatArray(DEFAULT_BANDS.size)
        }
        set(value) = prefs.edit().putString(KEY_EQ_GAINS, value.joinToString(",")).apply()

    var eqPreamp: Float
        get() = prefs.getFloat(KEY_EQ_PREAMP, 0f)
        set(value) = prefs.edit().putFloat(KEY_EQ_PREAMP, value).apply()

    var eqBass: Int
        get() = prefs.getInt(KEY_EQ_BASS, 0)
        set(value) = prefs.edit().putInt(KEY_EQ_BASS, value).apply()

    var eqVirtualizer: Int
        get() = prefs.getInt(KEY_EQ_VIRT, 0)
        set(value) = prefs.edit().putInt(KEY_EQ_VIRT, value).apply()

    var eqPresetName: String
        get() = prefs.getString(KEY_EQ_PRESET, "Flat") ?: "Flat"
        set(value) = prefs.edit().putString(KEY_EQ_PRESET, value).apply()

    /* -------------------------- lyrics overrides ------------------------- */

    fun saveLyricsOverride(songId: Long, raw: String) {
        prefs.edit().putString("lyrics_$songId", raw).apply()
    }

    fun lyricsOverride(songId: Long): String? = prefs.getString("lyrics_$songId", null)

    fun clearLyricsOverride(songId: Long) {
        prefs.edit().remove("lyrics_$songId").apply()
    }

    /* --------------------------- AI assistant -------------------------- */

    var aiApiKey: String
        get() = prefs.getString(KEY_AI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AI_KEY, value).apply()

    var aiBaseUrl: String
        get() = prefs.getString(KEY_AI_BASE, DEFAULT_AI_BASE) ?: DEFAULT_AI_BASE
        set(value) = prefs.edit().putString(KEY_AI_BASE, value).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    /** Selected assistant backend: [PROVIDER_GEMINI] or [PROVIDER_OPENAI]. */
    var aiProviderKey: String
        get() = prefs.getString(KEY_AI_PROVIDER, PROVIDER_OPENAI) ?: PROVIDER_OPENAI
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    /** Rebuild the one-playlist-per-artist set after every library scan. */
    var autoArtistPlaylists: Boolean
        get() = prefs.getBoolean(KEY_AI_ARTIST_PLAYLISTS, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_ARTIST_PLAYLISTS, value).apply()

    /* --------------------------- smart playlist --------------------------- */

    var recentSearches: List<String>
        get() = runCatching {
            val array = JSONArray(prefs.getString(KEY_SEARCHES, "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.take(8).forEach(array::put)
            prefs.edit().putString(KEY_SEARCHES, array.toString()).apply()
        }

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    fun smartPlaylistCount(kind: SmartKind): Int = prefs.getInt("smart_count_${kind.key}", 0)

    fun saveSmartPlaylistDump(kind: SmartKind, ids: List<Long>) {
        prefs.edit().putString("smart_ids_${kind.key}", ids.joinToString(",")).apply()
    }

    fun smartPlaylistDump(kind: SmartKind): List<Long> =
        prefs.getString("smart_ids_${kind.key}", null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    /** Debug-friendly dump of user settings for the about screen. */
    fun summaryJson(): String = JSONObject(
        mapOf(
            "eqEnabled" to eqEnabled,
            "preset" to eqPresetName,
            "bass" to eqBass,
            "virtualizer" to eqVirtualizer,
        ),
    ).toString()

    companion object {
        val DEFAULT_BANDS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

        private const val KEY_QUEUE = "queue_ids"
        private const val KEY_INDEX = "queue_index"
        private const val KEY_POSITION = "queue_position"
        private const val KEY_SONG = "last_song"
        private const val KEY_LAST_SCAN = "last_scan"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_SORT = "sort"
        private const val KEY_SLEEP = "sleep_timer"
        private const val KEY_CROSSFADE = "crossfade"
        private const val KEY_EQ_ON = "eq_on"
        private const val KEY_EQ_GAINS = "eq_gains"
        private const val KEY_EQ_PREAMP = "eq_preamp"
        private const val KEY_EQ_BASS = "eq_bass"
        private const val KEY_EQ_VIRT = "eq_virt"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_SEARCHES = "recent_searches"
        private const val KEY_SPEED = "speed"
        private const val KEY_AI_KEY = "ai_api_key"
        private const val KEY_AI_BASE = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_ARTIST_PLAYLISTS = "ai_artist_playlists"
        private const val KEY_AI_PROVIDER = "ai_provider"

        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GEMINI = "gemini"

        const val DEFAULT_AI_BASE = "https://api.z.ai/api/paas/v4"
        const val DEFAULT_AI_MODEL = "glm-4-flash"
    }
}
