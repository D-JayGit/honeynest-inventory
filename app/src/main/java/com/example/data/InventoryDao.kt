package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    // Inventory Items
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getInventoryItemByBarcode(barcode: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryItemById(id: Long): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItem): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItem)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItem)

    // Stock Transactions
    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getTransactionsForItem(itemId: Long): Flow<List<StockTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction): Long

    // Reconciliation Sessions
    @Query("SELECT * FROM reconciliation_sessions ORDER BY date DESC")
    fun getAllReconciliationSessions(): Flow<List<ReconciliationSession>>

    @Query("SELECT * FROM reconciliation_sessions WHERE id = :sessionId")
    suspend fun getReconciliationSessionById(sessionId: Long): ReconciliationSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationSession(session: ReconciliationSession): Long

    @Update
    suspend fun updateReconciliationSession(session: ReconciliationSession)

    @Query("DELETE FROM reconciliation_sessions WHERE id = :sessionId")
    suspend fun deleteReconciliationSession(sessionId: Long)

    // Reconciliation Items
    @Query("SELECT * FROM reconciliation_items WHERE sessionId = :sessionId")
    fun getReconciliationItemsForSession(sessionId: Long): Flow<List<ReconciliationItem>>

    @Query("SELECT * FROM reconciliation_items WHERE sessionId = :sessionId")
    suspend fun getReconciliationItemsForSessionSync(sessionId: Long): List<ReconciliationItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationItem(item: ReconciliationItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationItems(items: List<ReconciliationItem>)

    @Update
    suspend fun updateReconciliationItem(item: ReconciliationItem)
}
