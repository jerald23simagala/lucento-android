package com.lucento.app.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Lucento Pro, Phase 4: only purchases Google Play signed are honoured (docs/MONETIZATION.md §9 "Verification"). */
class PurchaseSignatureTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val json = """{"orderId":"GPA.1234","packageName":"com.lucento.app","productId":"lucento_pro_lifetime","purchaseState":0}"""

    private fun sign(data: String, private: java.security.PrivateKey = keys.private): String =
        Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA1withRSA").run {
                initSign(private)
                update(data.toByteArray(Charsets.UTF_8))
                sign()
            },
        )

    @Test fun a_purchase_play_signed_is_honoured() {
        assertTrue(PurchaseSignature.verify(publicKey, json, sign(json)))
    }

    @Test fun edited_purchase_data_fails() {
        val signature = sign(json)
        assertFalse(PurchaseSignature.verify(publicKey, json.replace("lucento_pro_lifetime", "lucento_pro"), signature))
    }

    @Test fun a_signature_from_another_key_fails() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(PurchaseSignature.verify(publicKey, json, sign(json, other.private)))
    }

    @Test fun no_key_no_signature_or_garbage_never_verifies_and_never_throws() {
        assertFalse("a build without the licence key honours nothing", PurchaseSignature.verify("", json, sign(json)))
        assertFalse(PurchaseSignature.verify(publicKey, json, ""))
        assertFalse(PurchaseSignature.verify(publicKey, "", sign(json)))
        assertFalse(PurchaseSignature.verify("not base64 at all!", json, sign(json)))
        assertFalse(PurchaseSignature.verify(publicKey, json, "AAAA"))
    }
}
