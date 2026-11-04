package com.lucento.app.core.banks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bundled institution registry, parsed once on an IO thread the first time anything asks for it. Offline and
 * local: nothing is fetched. A registry that fails to load (it never should) leaves wallets on their per-type look.
 */
class InstitutionRepository(private val read: () -> String) {
    private val mutex = Mutex()
    @Volatile private var loaded: InstitutionRegistry? = null

    suspend fun registry(): InstitutionRegistry = loaded ?: mutex.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            runCatching { InstitutionRegistry.parse(read()) }
                .onFailure { android.util.Log.w("Lucento", "Institution registry failed to load", it) }
                .getOrDefault(InstitutionRegistry.EMPTY)
        }.also { loaded = it }
    }

    /** Emits the registry once it is loaded; combine with other flows so screens show institutions when ready. */
    fun observe(): Flow<InstitutionRegistry> = flow { emit(registry()) }
}
