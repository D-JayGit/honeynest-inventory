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
        ReconciliationItem::class,
        BillingSession::class,
        BillingSessionItem::class
    ],
    version = 9,
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
                .addMigrations(MIGRATION_3_5, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = createBillingTables(db)
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = createBillingTables(db)
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE billing_session_items ADD COLUMN unitCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    "UPDATE billing_session_items SET unitCost = " +
                        "COALESCE((SELECT purchasePrice FROM inventory_items WHERE inventory_items.id = billing_session_items.itemId), 0.0)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE billing_sessions ADD COLUMN discountPercentage REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE billing_sessions ADD COLUMN discountType TEXT NOT NULL DEFAULT 'PERCENTAGE'")
                db.execSQL("ALTER TABLE billing_sessions ADD COLUMN discountValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE billing_sessions SET discountValue = discountPercentage")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN colorName TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun createBillingTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS billing_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "customerName TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "status TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS billing_session_items (" +
                    "sessionId INTEGER NOT NULL, " +
                    "itemId INTEGER NOT NULL, " +
                    "productName TEXT NOT NULL, " +
                    "barcode TEXT NOT NULL, " +
                    "sku TEXT, " +
                    "unitPrice REAL NOT NULL, " +
                    "quantity INTEGER NOT NULL, " +
                    "PRIMARY KEY(sessionId, itemId), " +
                    "FOREIGN KEY(sessionId) REFERENCES billing_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
        }
    }
}
