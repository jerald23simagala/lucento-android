package com.lucento.app.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lucento.app.domain.capture.TextFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition with ML Kit's bundled Latin model. The image is read from local storage
 * and never leaves the device; the model is packaged in the APK, so this works offline.
 */
object ReceiptOcr {

    /** Recognised lines with their positions, for [com.lucento.app.domain.capture.ReceiptParser.mergeRows]. */
    suspend fun recognize(context: Context, uri: Uri): List<TextFragment> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val fragments = text.textBlocks.flatMap { block ->
                            block.lines.map { line ->
                                val box = line.boundingBox
                                TextFragment(
                                    text = line.text,
                                    left = box?.left ?: 0,
                                    top = box?.top ?: 0,
                                    right = box?.right ?: 0,
                                    bottom = box?.bottom ?: 0,
                                )
                            }
                        }
                        cont.resume(fragments)
                    }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
        } finally {
            recognizer.close()
        }
    }

    /** A downsampled preview (longest side ≤ [maxSide] px) for the review screen. */
    suspend fun preview(context: Context, uri: Uri, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }
}
