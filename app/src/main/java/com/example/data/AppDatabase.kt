package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InventoryItem::class,
        StockTransaction::class,
        ReconciliationSession::class,
        ReconciliationItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve legacy records if a prior version allowed duplicate barcodes.
                // The retained barcode keeps the earliest item; later records are clearly marked
                // for user review rather than being discarded or preventing the upgrade.
                db.execSQL(
                    "UPDATE inventory_items SET barcode = barcode || '-duplicate-' || id " +
                        "WHERE id NOT IN (SELECT MIN(id) FROM inventory_items GROUP BY barcode)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_barcode " +
                        "ON inventory_items (barcode)"
                )
            }
        }
    }
}
