package com.lucento.app.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Google Play signs every purchase it reports with the app's licence key (Play Console › Monetization setup). Checking
 * that signature on the phone means a purchase injected by a tampered Play Store, or typed into a patched app, is never
 * honoured (docs/MONETIZATION.md §9 "Verification"). The key is public by design; it reaches the build through
 * `LUCENTO_PLAY_LICENSE_KEY` like the other release settings, never typed into source.
 *
 * Pure JVM (java.security), so it is a unit test with a generated key pair.
 */
object PurchaseSignature {
    private const val ALGORITHM = "SHA1withRSA" // what Google Play uses for in-app billing signatures

    /** True only when [signature] is Play's signature of exactly [signedData] under [base64PublicKey]. Never throws. */
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank() || signedData.isEmpty() || signature.isBlank()) return false
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature.trim()))
            }
        }.getOrDefault(false)
    }
}
