package com.lucento.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * The profile picture: one square JPEG in the app's private files. It is never synced and never part of a backup, and
 * it is removed with the rest of the account's data when this phone's ledger is wiped (sign-out, account switch).
 *
 * It comes from the phone — the system photo picker, which needs no storage permission — or, only when the user taps
 * "Use my Google photo", from the signed-in Google account's own picture: one HTTPS download from Google's image host
 * ([isGooglePhotoUrl]), nothing sent but the request. That download is the only network use here, and it never happens
 * on its own (CLAUDE.md "Privacy & cost": any network use beyond sync needs its own opt-in).
 */
class ProfilePhotos(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY)
    private val file = File(directory, FILE_NAME)

    /** Copies the picked image in as the profile photo. False when it can't be read or saved; the old photo stays. */
    fun importFrom(uri: Uri): Boolean = try {
        val bitmap = decode { appContext.contentResolver.openInputStream(uri) }
        bitmap != null && save(bitmap)
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /**
     * Downloads the Google account's picture from [url] and keeps it as the profile photo. Refuses anything that isn't
     * Google's HTTPS image host, a non-image response, or more than [MAX_DOWNLOAD_BYTES]. Call off the main thread.
     */
    fun importFromGoogle(url: String): Boolean {
        if (!isGooglePhotoUrl(url)) return false
        return try {
            val bytes = download(sizedGoogleUrl(url)) ?: return false
            val bitmap = decode { bytes.inputStream() }
            bitmap != null && save(bitmap)
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** The saved photo, or null when there is none (or it can't be read). */
    fun load(): Bitmap? = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null

    /** Removes the photo. Safe when there is none. */
    fun delete() {
        directory.deleteRecursively()
    }

    private fun download(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            // A redirect may only land on Google's image host too.
            if (!isGooglePhotoUrl(connection.url.toString())) return null
            if (connection.contentType?.startsWith("image/") != true) return null
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Decodes downsampled (the source can be a 50-megapixel photo), then crops the centre square and scales it. */
    private fun decode(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null, so only a stream that couldn't be opened stops here.
        open()?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val source = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (side > EDGE_PX) Bitmap.createScaledBitmap(square, EDGE_PX, EDGE_PX, true) else square
        if (square !== source) source.recycle()
        if (scaled !== square) square.recycle()
        return scaled
    }

    /** Written to a temporary file first and moved into place, so a failed save never leaves half a photo. */
    private fun save(bitmap: Bitmap): Boolean {
        directory.mkdirs()
        val temp = File(directory, "$FILE_NAME.tmp")
        return try {
            FileOutputStream(temp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Could not encode the photo")
            }
            if (file.exists()) file.delete()
            temp.renameTo(file) || run {
                temp.copyTo(file, overwrite = true)
                temp.delete()
                true
            }
        } catch (e: IOException) {
            temp.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val DIRECTORY = "profile"
        private const val FILE_NAME = "photo.jpg"
        /** The saved photo's edge: crisp on the 56dp profile avatar at any density. */
        internal const val EDGE_PX = 384
        private const val JPEG_QUALITY = 88
        private const val TIMEOUT_MS = 10_000
        internal const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024

        /** Only Google's own HTTPS image host (lh3.googleusercontent.com and its siblings) is ever fetched. */
        fun isGooglePhotoUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && (host == GOOGLE_HOST || host.endsWith(".$GOOGLE_HOST"))
        }

        /**
         * Google serves its account pictures at 96px by default ("…=s96-c"); asks for [EDGE_PX] instead, so the avatar
         * isn't blurry. A URL without a size suffix is left as it is.
         */
        fun sizedGoogleUrl(url: String): String = SIZE_SUFFIX.replace(url, "=s$EDGE_PX-c")

        /** The largest power-of-two sample size that keeps the shorter edge at or above [EDGE_PX]. */
        internal fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            val shortest = min(width, height)
            while (shortest / (sample * 2) >= EDGE_PX && max(width, height) / (sample * 2) > 0) sample *= 2
            return sample
        }

        private const val GOOGLE_HOST = "googleusercontent.com"
        private val SIZE_SUFFIX = Regex("=s\\d+(-c)?$")
    }
}
