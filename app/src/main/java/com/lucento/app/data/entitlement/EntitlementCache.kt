package com.lucento.app.data.entitlement

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The last verified entitlements, kept on the phone so they apply in the first frame and whenever Play and the server are
 * unreachable (docs/MONETIZATION.md §5.3). Device-level: survives `LocalLedgerReset`, is never synced and never in a
 * backup file. A sign-out takes the account's own rows from the server out of it, and Google Play's reading stays
 * ([EntitlementManager.signedOut], 2026-11-14). Not a Boolean: a list of [Entitlement]s with their dates and verifiers.
 */
interface EntitlementCache {
    /** Null when nothing is stored, or the stored snapshot fails verification (tampered or copied from another phone). */
    suspend fun read(): List<Entitlement>?
    suspend fun write(entitlements: List<Entitlement>)
}

/** Where the sealed snapshot bytes live: DataStore on the phone, memory in tests. */
interface SnapshotStore {
    suspend fun read(): SealedSnapshot?
    suspend fun write(snapshot: SealedSnapshot)
}

data class SealedSnapshot(val payload: String, val seal: String)

/**
 * Binds a snapshot to this phone. On Android the key lives in the Android Keystore ([KeystoreSealer]); a snapshot copied
 * to another phone, or edited, fails [verify] and is ignored until Play or the account restores it. Not DRM: the servers
 * stay authoritative for everything that costs money.
 */
interface Sealer {
    fun seal(payload: String): String
    fun verify(payload: String, seal: String): Boolean
}

/** HMAC-SHA256 over the payload with a caller-provided key (JVM tests, and the Keystore sealer's fallback). */
class HmacSealer(private val key: ByteArray) : Sealer {
    override fun seal(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun verify(payload: String, seal: String): Boolean = constantTimeEquals(seal(payload), seal)
}

internal fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}

class InMemorySnapshotStore(var snapshot: SealedSnapshot? = null) : SnapshotStore {
    override suspend fun read(): SealedSnapshot? = snapshot
    override suspend fun write(snapshot: SealedSnapshot) {
        this.snapshot = snapshot
    }
}

/** The cache: JSON payload + seal. A missing, unreadable or tampered snapshot reads as "nothing known" (Free until verified). */
class SealedEntitlementCache(private val store: SnapshotStore, private val sealer: Sealer) : EntitlementCache {
    override suspend fun read(): List<Entitlement>? {
        val sealed = store.read() ?: return null
        if (!sealer.verify(sealed.payload, sealed.seal)) return null
        return EntitlementSnapshotCodec.decode(sealed.payload)
    }

    override suspend fun write(entitlements: List<Entitlement>) {
        val payload = EntitlementSnapshotCodec.encode(entitlements)
        store.write(SealedSnapshot(payload, sealer.seal(payload)))
    }
}

/** JSON for the snapshot. Unknown enum values (a newer app wrote them) make the whole snapshot unreadable, never a crash. */
object EntitlementSnapshotCodec {
    const val VERSION = 1

    @Serializable
    private data class Snapshot(val version: Int, val rows: List<Row>)

    @Serializable
    private data class Row(
        val source: String,
        val status: String,
        val grants: List<String>,
        val startsAt: Long,
        val expiresAt: Long?,
        val productId: String?,
        val basePlanId: String?,
        val tokenHash: String?,
        val verifiedAt: Long,
        val verifiedBy: String,
        val autoRenewing: Boolean,
        /** Added 2026-11-12 (docs §5.3 L-2). A snapshot written before it reads as checked when verified. */
        val checkedAt: Long? = null,
        /**
         * Added 2026-11-13 (docs §5.3 L-6): Lucento's last confirmation, [NEVER_CONFIRMED] for "not yet on this install".
         * A snapshot written before it reads as confirmed when verified, so the update itself pauses nobody.
         */
        val confirmedAt: Long? = null,
        /** Added 2026-11-13 (L-6): when the purchase check last answered for this purchase. */
        val lucentoCheckedAt: Long? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entitlements: List<Entitlement>): String = json.encodeToString(
        Snapshot(
            version = VERSION,
            rows = entitlements.map {
                Row(
                    source = it.source.name, status = it.status.name, grants = it.grants.map(Capability::name).sorted(),
                    startsAt = it.startsAtMillis, expiresAt = it.expiresAtMillis, productId = it.productId,
                    basePlanId = it.basePlanId, tokenHash = it.purchaseTokenHash, verifiedAt = it.verifiedAtMillis,
                    verifiedBy = it.verifiedBy.name, autoRenewing = it.autoRenewing, checkedAt = it.checkedAtMillis,
                    confirmedAt = it.confirmedAtMillis ?: NEVER_CONFIRMED, lucentoCheckedAt = it.lucentoCheckedAtMillis,
                )
            },
        ),
    )

    fun decode(payload: String): List<Entitlement>? = runCatching {
        val snapshot = json.decodeFromString<Snapshot>(payload)
        if (snapshot.version != VERSION) return null
        snapshot.rows.map {
            Entitlement(
                source = EntitlementSource.valueOf(it.source),
                status = EntitlementStatus.valueOf(it.status),
                grants = it.grants.mapTo(LinkedHashSet(), Capability::valueOf),
                startsAtMillis = it.startsAt,
                expiresAtMillis = it.expiresAt,
                productId = it.productId,
                basePlanId = it.basePlanId,
                purchaseTokenHash = it.tokenHash,
                verifiedAtMillis = it.verifiedAt,
                verifiedBy = Verifier.valueOf(it.verifiedBy),
                autoRenewing = it.autoRenewing,
                checkedAtMillis = it.checkedAt ?: it.verifiedAt,
                confirmedAtMillis = when (val confirmed = it.confirmedAt) {
                    null -> it.verifiedAt
                    NEVER_CONFIRMED -> null
                    else -> confirmed
                },
                lucentoCheckedAtMillis = it.lucentoCheckedAt,
            )
        }
    }.getOrNull()

    /** In [Row.confirmedAt]: Lucento's server hasn't confirmed this purchase on this install yet. */
    private const val NEVER_CONFIRMED = -1L
}
