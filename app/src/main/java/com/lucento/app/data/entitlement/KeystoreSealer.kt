package com.lucento.app.data.entitlement

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Seals the entitlement snapshot with an HMAC key that never leaves the Android Keystore, so the snapshot is bound to this
 * phone (docs/MONETIZATION.md §5.3 L-5). If the Keystore refuses (rare, device-specific), a random key kept in
 * app-private SharedPreferences stands in: the snapshot is then only tamper-evident, never a crash and never no cache.
 */
class KeystoreSealer private constructor(private val key: SecretKey) : Sealer {

    override fun seal(payload: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun verify(payload: String, seal: String): Boolean = constantTimeEquals(seal(payload), seal)

    companion object {
        private const val ALIAS = "lucento_entitlement_mac"
        private const val ALGORITHM = "HmacSHA256"
        private const val FALLBACK_PREFS = "lucento_entitlement_seal"
        private const val FALLBACK_KEY = "fallback_key"

        /** The Keystore-backed sealer, or the fallback when the Keystore is unusable on this device. */
        fun create(context: Context): Sealer = runCatching { KeystoreSealer(keystoreKey()) }.getOrElse { fallback(context) }

        private fun keystoreKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build(),
            )
            return generator.generateKey()
        }

        private fun fallback(context: Context): Sealer {
            val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(FALLBACK_KEY, null)
            val key = if (stored != null) {
                Base64.decode(stored, Base64.NO_WRAP)
            } else {
                ByteArray(32).also { SecureRandom().nextBytes(it) }.also {
                    prefs.edit().putString(FALLBACK_KEY, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
                }
            }
            return HmacSealer(key)
        }
    }
}
