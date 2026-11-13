package com.zeroplayer.app.core

import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Tiny in-memory log ring buffer for the playback path.
 *
 * Every line is mirrored to logcat under one fixed tag so a single
 * `adb logcat -s ZeroPlayer` shows the whole pipeline, and is also kept in
 * memory so the Settings screen can display (and copy) it when no adb
 * connection is available.
 */
object DiagLog {

    const val TAG = "ZeroPlayer"
    private const val MAX_LINES = 150

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun d(scope: String, message: String) = append(scope, message, null)

    fun e(scope: String, message: String, error: Throwable? = null) = append(scope, message, error)

    private fun append(scope: String, message: String, error: Throwable?) {
        val stamp = LocalTime.now().format(timeFormat)
        val line = "$stamp [$scope] $message"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        if (error == null) {
            Log.d(TAG, "$scope: $message")
        } else {
            Log.e(TAG, "$scope: $message", error)
        }
    }

    /** "CauseClass: message <- cause @ Frame.method:line" for a bug report. */
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .take(4)
            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
        val frames = error.stackTrace
            .take(6)
            .joinToString(" | ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return "$chain @ $frames"
    }

    /**
     * Newest-last slice of the buffer restricted to the given scopes, used by
     * the Online Music diagnostics panel so it can show only provider traffic.
     */
    fun recent(scopes: Set<String>, limit: Int = 40): List<String> = synchronized(lines) {
        if (scopes.isEmpty()) return emptyList()
        val marker = scopes.map { "[$it] " }
        lines.asReversed()
            .asSequence()
            .filter { line -> marker.any { token -> line.contains(token) } }
            .take(limit)
            .toList()
            .asReversed()
    }

    fun dump(): String = synchronized(lines) {
        if (lines.isEmpty()) "No events yet." else lines.joinToString("\n")
    }

    fun clear() = synchronized(lines) { lines.clear() }
}
