package com.zeroplayer.app.net

import com.zeroplayer.app.data.Prefs
import com.zeroplayer.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Resolved assistant configuration, editable from Settings → AI. */
data class AiConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    val isReady: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/**
 * Small OpenAI-compatible chat client used by the optional assistant.
 *
 * The app talks to the provider directly with the key the user typed in
 * Settings; nothing is proxied and no key is bundled with the APK. Everything
 * here is opt-in: without a key the client throws [IOException] and the UI
 * simply reports that the assistant is not configured.
 */
class AiClient(private val prefs: Prefs) {

    val config: AiConfig
        get() = AiConfig(
            baseUrl = prefs.aiBaseUrl.trim(),
            model = prefs.aiModel.trim(),
            apiKey = prefs.aiApiKey.trim(),
        )

    val isReady: Boolean get() = config.isReady

    /** Sends one system/user pair and returns the assistant's trimmed answer. */
    suspend fun complete(system: String, user: String, temperature: Double = 0.3): String {
        val cfg = config
        if (!cfg.isReady) {
            throw IOException("The assistant is not configured. Add an API key in Settings → AI.")
        }
        return withContext(Dispatchers.IO) { post(cfg, system, user, temperature) }
    }

    /** Cheap round trip used by the "Test connection" button. */
    suspend fun test(): String = complete(
        system = "You are a connectivity check. Reply with the single word OK.",
        user = "ping",
        temperature = 0.0,
    )

    /**
     * Asks the model for the plain-text lyrics of a track. The answer is stored
     * as an AI-sourced lyrics entry so it shows up in the normal lyrics screen.
     */
    suspend fun findLyrics(song: Song): String {
        val answer = complete(
            system = LYRIC_SYSTEM_PROMPT,
            user = buildString {
                appendLine("Title: ${song.title}")
                appendLine("Artist: ${song.artist}")
                appendLine("Album: ${song.album}")
                appendLine("Duration: ${song.durationMs / 1000}s")
                appendLine()
                append("Write the lyrics of this song.")
            },
            temperature = 0.2,
        )
        val cleaned = sanitizeLyrics(answer)
        if (cleaned.isBlank()) throw IOException("The model returned no lyrics for this track.")
        return cleaned
    }

    /**
     * Guesses the performing artist for a track whose tags carry no usable
     * artist. Returns null when the model is not confident.
     */
    suspend fun guessArtist(song: Song, fileName: String): String? {
        val answer = complete(
            system = ARTIST_SYSTEM_PROMPT,
            user = buildString {
                appendLine("File name: $fileName")
                appendLine("Title tag: ${song.title}")
                appendLine("Album tag: ${song.album}")
                appendLine("Folder: ${song.folderName}")
                append("Who is the performing artist?")
            },
            temperature = 0.0,
        )
        val first = answer.lineSequence().firstOrNull().orEmpty()
            .trim()
            .removeSurrounding("\"")
            .removeSuffix(".")
            .trim()
        val unknown = first.equals("UNKNOWN", ignoreCase = true) ||
            first.equals("unknown artist", ignoreCase = true) ||
            first.isBlank() ||
            first.length > 80
        return if (unknown) null else first
    }

    private fun post(cfg: AiConfig, system: String, user: String, temperature: Double): String {
        val endpoint = cfg.baseUrl.trimEnd('/').let {
            if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
        }
        val payload = JSONObject().apply {
            put("model", cfg.model)
            put("temperature", temperature)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }.toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code · ${body.take(240).ifBlank { "no response body" }}")
            }
            val content = try {
                JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
            } catch (e: JSONException) {
                throw IOException("The provider sent an unexpected response.")
            }
            if (content.isBlank()) throw IOException("The model returned an empty answer.")
            return content.trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun sanitizeLyrics(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```").removePrefix("text").removePrefix("lrc").trim()
            val fence = text.lastIndexOf("```")
            if (fence >= 0) text = text.substring(0, fence).trim()
        }
        val firstLine = text.lineSequence().firstOrNull().orEmpty().lowercase()
        if (firstLine.startsWith("lyrics") && firstLine.length < 40 && text.contains('\n')) {
            text = text.substringAfter('\n').trim()
        }
        return text
    }

    private companion object {
        const val LYRIC_SYSTEM_PROMPT =
            "You provide song lyrics as plain text. Reply with the lyric lines only: no title, " +
                "no timestamps, no section labels, no commentary, no code fences. Never add a note " +
                "about uncertainty. If you do not know the song, reply with the best lyrics you " +
                "know that match the given title and artist."

        const val ARTIST_SYSTEM_PROMPT =
            "You identify the performing artist of a music file from its file name and tags. " +
                "Reply with the artist name only, without quotes or explanation. " +
                "If you cannot tell, reply with exactly UNKNOWN."
    }
}
