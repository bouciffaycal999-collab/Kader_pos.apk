package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        InvoiceEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        SupplierMovementEntity::class,
        CaisseMovementEntity::class,
        DailyCaisseCountEntity::class,
        StaffUserEntity::class,
        RolePolicyEntity::class,
        NotificationEntity::class,
        AppKvEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN pricingUnit TEXT NOT NULL DEFAULT 'carton'")
                db.execSQL("ALTER TABLE products ADD COLUMN prixPiece REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN vendeurTel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN hidePrices INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN whatsappSentBeforePrint INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE staff_users ADD COLUMN canViewPrixAchat INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN canModifyPrices INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE staff_users SET canViewPrixAchat = 1, canModifyPrices = 1 WHERE role IN ('admin', 'gerant')")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `role_policies` (
                        `roleId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `canViewPrixAchat` INTEGER NOT NULL,
                        `canModifyPrices` INTEGER NOT NULL,
                        `canModifyStock` INTEGER NOT NULL,
                        `canEditInvoices` INTEGER NOT NULL,
                        `canDeleteInvoices` INTEGER NOT NULL,
                        `canViewSuppliers` INTEGER NOT NULL,
                        `canViewCaisse` INTEGER NOT NULL,
                        `canManageUsers` INTEGER NOT NULL,
                        `allowedTabsJson` TEXT NOT NULL,
                        PRIMARY KEY(`roleId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kader_ceramic_pos.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
