package com.lucento.app.data.ai

import java.net.HttpURLConnection
import java.net.URL

/** A proxy's reply: the HTTP status, and the body when it is 200. */
internal data class ProxyReply(val code: Int, val body: String?)

/**
 * One request to Lucento's own proxies (`api/interpret`, `api/ask`) with the signed-in session's token. A token can be
 * refused (401) when it expired while the phone slept and the session hadn't refreshed yet; then a refreshed token is
 * asked for ([accessToken] with `refresh = true`) and the request is sent once more — never more, so a session that
 * really ended still shows as signed out. Null when no account is signed in (nothing is sent).
 */
internal suspend fun postToProxy(
    endpoint: String,
    body: String,
    readTimeoutMs: Int,
    accessToken: suspend (refresh: Boolean) -> String?,
): ProxyReply? {
    val token = accessToken(false) ?: return null
    val first = post(endpoint, body, readTimeoutMs, token)
    if (first.code != 401) return first
    val fresh = accessToken(true)?.takeIf { it != token } ?: return first
    return post(endpoint, body, readTimeoutMs, fresh)
}

private fun post(endpoint: String, body: String, readTimeoutMs: Int, token: String): ProxyReply {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 8_000
        readTimeout = readTimeoutMs
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $token")
    }
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val text = if (code == 200) connection.inputStream.bufferedReader(Charsets.UTF_8).readText() else null
        return ProxyReply(code, text)
    } finally {
        connection.disconnect()
    }
}
