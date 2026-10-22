package com.lucento.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local-first source of truth. Schema JSON is exported to app/schemas for migration review.
 * Every future version bump must ship an explicit [androidx.room.migration.Migration];
 * destructive fallback is intentionally not enabled because it would erase financial data.
 */
@Database(
    entities = [WalletEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LucentoDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        private const val NAME = "lucento.db"

        fun build(context: Context): LucentoDatabase =
            Room.databaseBuilder(context.applicationContext, LucentoDatabase::class.java, NAME)
                .build()
    }
}
