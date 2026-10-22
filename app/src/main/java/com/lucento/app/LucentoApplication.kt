package com.lucento.app

import android.app.Application
import com.lucento.app.data.local.LucentoDatabase
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.WalletRepository

class LucentoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Minimal manual dependency container. Swap for Hilt if the graph grows substantially. */
class AppContainer(application: Application) {
    private val database: LucentoDatabase by lazy { LucentoDatabase.build(application) }
    val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository(application) }
    val wallets: WalletRepository by lazy { WalletRepository(database.walletDao()) }
}
