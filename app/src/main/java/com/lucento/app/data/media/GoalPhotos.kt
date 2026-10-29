package com.lucento.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/**
 * A savings goal's own picture. The chosen image is copied into the app's private files folder and downscaled, so the
 * goal keeps it without depending on the original file. Photos never leave the device.
 *
 * Photos are not part of a JSON backup. Restoring a goal from a file keeps its scene and drops a custom photo,
 * because the picture file never leaves this device.
 */
class GoalPhotos(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY)

    /** Copies [uri] into app storage and returns the copy's absolute path, or null if it can't be read or saved. */
    fun import(uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null when only bounds are requested, so report the stream as opened instead.
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true }
                ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
            val bitmap = appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            directory.mkdirs()
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            try {
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Could not encode the picture")
                }
                file.absolutePath
            } catch (e: IOException) {
                file.delete()
                null
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Removes a photo copy. Paths outside the photo folder are ignored, so this can never delete other files. */
    fun delete(path: String?) {
        if (path == null) return
        val file = File(path).absoluteFile
        if (file.parentFile == directory.absoluteFile) file.delete()
    }

    /** Removes every saved goal photo. Used when this phone's ledger is wiped on sign-out. */
    fun deleteAll() {
        directory.deleteRecursively()
    }

    companion object {
        const val DIRECTORY = "goal_photos"
        private const val MAX_EDGE_PX = 1280
        private const val JPEG_QUALITY = 88

        /** The largest power-of-two sample size that keeps the longer edge at or above [MAX_EDGE_PX]. */
        internal fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            val longest = max(width, height)
            while (longest / (sample * 2) >= MAX_EDGE_PX) sample *= 2
            return sample
        }
    }
}
