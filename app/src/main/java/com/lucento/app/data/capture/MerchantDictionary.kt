package com.lucento.app.data.capture

import com.lucento.app.domain.capture.merchant.MerchantDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bundled merchant and payment-provider dictionaries (assets/merchants, one file per region, docs/SCAN.md), read
 * once on first use. They ship with the app and are looked up on the phone; nothing is downloaded. A file that can't be
 * read is skipped, so scanning always works, with fewer names known at worst.
 */
class MerchantDictionary(private val read: () -> List<String>) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: MerchantDirectory? = null

    suspend fun directory(): MerchantDirectory = loaded ?: mutex.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            MerchantDirectory.parse(runCatching(read).getOrDefault(emptyList()))
        }.also { loaded = it }
    }
}
