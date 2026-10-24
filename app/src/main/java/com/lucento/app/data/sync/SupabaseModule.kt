package com.lucento.app.data.sync

import com.lucento.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds the optional Supabase client for cloud sync (see docs/SYNC.md). Lucento ships with no mandatory
 * cloud dependency: when the project isn't configured — the common case for a fresh checkout, since the
 * values come from `local.properties`/CI secrets and are never committed — [create] returns null and every
 * caller (Settings UI, [SyncRepository], [AuthRepository]) treats that as "cloud sync unavailable", not a
 * crash.
 */
object SupabaseModule {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val isGoogleSignInConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun create(): SupabaseClient? {
        if (!isConfigured) return null
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
