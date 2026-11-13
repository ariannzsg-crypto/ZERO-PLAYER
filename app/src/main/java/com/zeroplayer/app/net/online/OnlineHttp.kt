package com.zeroplayer.app.net.online

import com.zeroplayer.app.core.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client for the online catalogues.
 *
 * The project already talks to the network with `HttpURLConnection` (see
 * `AiClient`) and has no Retrofit/OkHttp dependency, so the online section
 * reuses the same approach instead of pulling a second HTTP stack in.
 */
class OnlineHttp {

    data class Response(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299

        fun json(): JSONObject = runCatching { JSONObject(body) }
            .getOrElse { throw IOException("The provider sent a response this app cannot read.") }

        fun obj(): JSONObject? = if (isSuccessful) runCatching { JSONObject(body) }.getOrNull() else null

        /** Throws a readable message for a failed call. */
        fun requireSuccess() {
            if (!isSuccessful) throw IOException(describeFailure(code, body))
        }
    }

    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
    ): Response = withContext(Dispatchers.IO) {
        val parsed = runCatching { URL(url) }.getOrNull()
        val scope = scopeOf(parsed?.host.orEmpty(), parsed?.path.orEmpty())
        val target = safeTarget(parsed)
        val startedAt = System.currentTimeMillis()
        DiagLog.d(scope, "-> $method $target")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val elapsed = System.currentTimeMillis() - startedAt
            when {
                code in 200..299 -> DiagLog.d(scope, "<- $code $method $target (${elapsed}ms)")
                code in 400..499 -> DiagLog.d(
                    scope,
                    "<- $code $method $target (${elapsed}ms): ${redact(text).take(160)}",
                )
                else -> DiagLog.e(
                    scope,
                    "<- $code $method $target (${elapsed}ms): ${redact(text).take(160)}",
                )
            }
            Response(code, text)
        } catch (io: IOException) {
            DiagLog.e(
                scope,
                "x $method $target after ${System.currentTimeMillis() - startedAt}ms: ${DiagLog.describe(io)}",
                io,
            )
            throw IOException(friendlyNetworkMessage(io), io)
        } finally {
            connection.disconnect()
        }
    }

    companion object {

        /**
         * Which provider a call belongs to, derived from the URL so every call
         * site is traced without touching it: api.spotify.com and
         * account.spotify.com log as SPOTIFY, api.soundcloud.com as SOUNDCLOUD,
         * and the Vercel backend by the provider named in its path.
         */
        fun scopeOf(host: String, path: String): String {
            val lowerHost = host.lowercase()
            val lowerPath = path.lowercase()
            return when {
                lowerHost.endsWith("spotify.com") -> "SPOTIFY"
                lowerHost.endsWith("soundcloud.com") -> "SOUNDCLOUD"
                lowerHost.endsWith("youtube.com") || lowerHost.endsWith("googleapis.com") -> "YOUTUBE"
                "spotify" in lowerPath -> "SPOTIFY"
                "soundcloud" in lowerPath -> "SOUNDCLOUD"
                "youtube" in lowerPath -> "YOUTUBE"
                else -> "BACKEND"
            }
        }

        /** Host, path and a truncated query: never headers, tokens or cookies. */
        fun safeTarget(parsed: URL?): String {
            if (parsed == null) return "<invalid url>"
            val query = parsed.query?.let { raw ->
                "?" + if (raw.length > 90) raw.take(90) + "..." else raw
            }.orEmpty()
            return "${parsed.host}${parsed.path}$query"
        }

        /** Token-shaped values are masked before an error body reaches the log. */
        fun redact(text: String): String = text
            .replace(
                Regex("\"(access_token|refresh_token|id_token|code|code_verifier)\"\\s*:\\s*\"[^\"]*\""),
                "\"\$1\":\"***\"",
            )
            .replace(Regex("(access_token|refresh_token|code_verifier)=[^&\"]+"), "\$1=***")

        /** Same HTML/JSON distinction the AI client learned the hard way. */
        fun looksLikeHtml(body: String): Boolean {
            val head = body.trimStart().take(200).lowercase()
            return head.startsWith("<") || head.contains("<!doctype") || head.contains("<html")
        }

        fun describeFailure(code: Int, body: String): String {
            if (looksLikeHtml(body)) {
                return when (code) {
                    403 -> "$code · the provider's gateway blocked the request before it reached the API " +
                        "(no key or token check). This is a network or region block; a VPN usually fixes it."
                    429 -> "$code · the provider is rate limiting this network. Try again shortly."
                    502, 503, 504 -> "$code · the provider's gateway is unavailable right now."
                    else -> "$code · the provider answered with a web page instead of an API reply, " +
                        "which points at a network block or an outage."
                }
            }
            val message = runCatching {
                val root = JSONObject(body)
                root.optJSONObject("error")?.optString("message").orEmpty().ifBlank {
                    root.optJSONObject("error")?.optString("error_description").orEmpty()
                }
            }.getOrDefault("")
            return if (message.isNotBlank()) "$code · ${message.trim().take(300)}" else "$code · ${body.take(200)}"
        }

        fun friendlyNetworkMessage(error: IOException): String = when {
            error.message?.contains("Unable to resolve host", true) == true ||
                error.message?.contains("No address associated", true) == true ->
                "No connection to the provider. Check the network or the backend URL."
            error is java.net.SocketTimeoutException -> "The provider took too long to answer."
            error is javax.net.ssl.SSLException -> "The secure connection to the provider failed."
            else -> error.message ?: "Network request failed."
        }
    }
}
