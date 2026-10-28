package com.lucento.app.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Have I Been Pwned k-anonymity check (https://haveibeenpwned.com/API/v3#PwnedPasswords).
 * Only the first 5 hex characters of the SHA-1 are sent. The password itself never leaves the device.
 * A network or server failure returns null from [PasswordPolicy.problem]'s lookup and does not block sign-up.
 */
internal object PwnedPasswordRange {
    fun sha1Hex(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    /** The only part of the hash that is sent. Always 5 characters. */
    fun prefix(hash: String): String = hash.take(5)

    /** True when [rangeBody] lists this hash's suffix with a count above zero (padding rows are zero). */
    fun isBreached(hash: String, rangeBody: String): Boolean {
        val suffix = hash.drop(5)
        return rangeBody.lineSequence().any { line ->
            val parts = line.trim().split(':')
            parts.size == 2 && parts[0].equals(suffix, ignoreCase = true) && (parts[1].toIntOrNull() ?: 0) > 0
        }
    }
}

/** Length and breach check for a new password. [fetchRange] receives only the 5-character prefix. */
class PasswordPolicy(
    private val fetchRange: suspend (prefix: String) -> String? = ::fetchHibpRange,
) {
    suspend fun problem(password: String): String? {
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Use at least $MIN_PASSWORD_LENGTH characters for the password."
        }
        val hash = PwnedPasswordRange.sha1Hex(password)
        val prefix = PwnedPasswordRange.prefix(hash)
        if (prefix.length != 5) return null
        val body = try {
            fetchRange(prefix)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return if (PwnedPasswordRange.isBreached(hash, body)) {
            "This password has appeared in a data breach. Choose a different one."
        } else {
            null
        }
    }
}

/** HTTPS range lookup. Returns null on any failure so the caller can fail open. */
internal suspend fun fetchHibpRange(prefix: String): String? = withContext(Dispatchers.IO) {
    if (prefix.length != 5 || prefix.any { it !in '0'..'9' && it !in 'A'..'F' }) return@withContext null
    try {
        val connection = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.setRequestProperty("User-Agent", "Lucento")
        connection.setRequestProperty("Add-Padding", "true")
        if (connection.responseCode != 200) return@withContext null
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
