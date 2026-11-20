package com.lucento.app.domain.capture

import java.util.Locale

/**
 * What the composer's reply says when one message held more than is read at once: more than [BatchEntryParser.MAX_ROWS]
 * transactions, or more than [BatchEntryParser.MAX_INPUT] characters. Both readers (on this phone and Smarter reading)
 * stop at the same limits; the rest is never dropped silently. Null when everything was read.
 */
object CaptureLimits {

    fun note(leftOut: Int, inputCut: Boolean): String? = when {
        leftOut > 0 -> "Read the first ${BatchEntryParser.MAX_ROWS} transactions. Send the rest in another message."
        inputCut -> "Read the first ${String.format(Locale.US, "%,d", BatchEntryParser.MAX_INPUT)} characters. Send the rest in another message."
        else -> null
    }
}
