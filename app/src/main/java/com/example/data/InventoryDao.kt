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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryItem(item: InventoryItem): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItem)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllInventoryItems()

    @Query("SELECT * FROM inventory_items")
    suspend fun getAllInventoryItemsSync(): List<InventoryItem>

    @Transaction
    suspend fun deleteAllInventoryItemsAndRecord() {
        getAllInventoryItemsSync().forEach { item ->
            insertTransaction(
                StockTransaction(
                    itemId = item.id,
                    barcode = item.barcode,
                    itemName = item.name,
                    changeQuantity = -item.currentQuantity,
                    transactionType = "MANUAL_SUB",
                    note = "Item removed during inventory reset"
                )
            )
        }
        deleteAllInventoryItems()
    }

    @Transaction
    suspend fun saveInventoryItemAndTransaction(
        item: InventoryItem,
        transaction: StockTransaction?
    ): Long {
        val itemId = if (item.id == 0L) {
            insertInventoryItem(item)
        } else {
            updateInventoryItem(item)
            item.id
        }
        transaction?.let { insertTransaction(it.copy(itemId = itemId)) }
        return itemId
    }

    @Transaction
    suspend fun applyBarcodeStockChange(
        barcode: String,
        requestedChange: Int,
        transactionType: String,
        note: String
    ): Pair<InventoryItem, Int>? {
        val item = getInventoryItemByBarcode(barcode) ?: return null
        val updatedQuantity = (item.currentQuantity + requestedChange).coerceAtLeast(0)
        val appliedChange = updatedQuantity - item.currentQuantity
        val updatedItem = item.copy(
            currentQuantity = updatedQuantity,
            updatedAt = System.currentTimeMillis()
        )
        updateInventoryItem(updatedItem)
        if (appliedChange != 0) {
            insertTransaction(
                StockTransaction(
                    itemId = item.id,
                    barcode = item.barcode,
                    itemName = item.name,
                    changeQuantity = appliedChange,
                    transactionType = transactionType,
                    note = note
                )
            )
        }
        return updatedItem to appliedChange
    }

    @Transaction
    suspend fun deleteInventoryItemAndRecord(item: InventoryItem) {
        insertTransaction(
            StockTransaction(
                itemId = item.id,
                barcode = item.barcode,
                itemName = item.name,
                changeQuantity = -item.currentQuantity,
                transactionType = "MANUAL_SUB",
                note = "Item deleted from database"
            )
        )
        deleteInventoryItem(item)
    }

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

    @Transaction
    suspend fun createReconciliationSessionWithItems(
        session: ReconciliationSession,
        items: List<ReconciliationItem>
    ): Long {
        val sessionId = insertReconciliationSession(session)
        insertReconciliationItems(items.map { it.copy(sessionId = sessionId) })
        return sessionId
    }

    @Transaction
    suspend fun deleteReconciliationSessionWithItems(sessionId: Long) {
        deleteReconciliationItemsForSession(sessionId)
        deleteReconciliationSession(sessionId)
    }

    // Reconciliation Items
    @Query("SELECT * FROM reconciliation_items WHERE sessionId = :sessionId")
    fun getReconciliationItemsForSession(sessionId: Long): Flow<List<ReconciliationItem>>

    @Query("SELECT * FROM reconciliation_items WHERE sessionId = :sessionId")
    suspend fun getReconciliationItemsForSessionSync(sessionId: Long): List<ReconciliationItem>

    @Query("DELETE FROM reconciliation_items WHERE sessionId = :sessionId")
    suspend fun deleteReconciliationItemsForSession(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationItem(item: ReconciliationItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationItems(items: List<ReconciliationItem>)

    @Update
    suspend fun updateReconciliationItem(item: ReconciliationItem)

    @Transaction
    suspend fun updateReconciliationItemAndSessionStats(item: ReconciliationItem) {
        updateReconciliationItem(item)
        val sessionItems = getReconciliationItemsForSessionSync(item.sessionId)
        val session = getReconciliationSessionById(item.sessionId) ?: return
        val verifiedCount = sessionItems.count { it.isVerified }
        updateReconciliationSession(
            session.copy(
                verifiedItemsCount = verifiedCount,
                status = if (sessionItems.isNotEmpty() && verifiedCount == sessionItems.size) "COMPLETED" else "OPEN"
            )
        )
    }
}
