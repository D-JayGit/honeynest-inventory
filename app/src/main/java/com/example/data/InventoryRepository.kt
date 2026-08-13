package com.example.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val dao: InventoryDao) {

    val allItems: Flow<List<InventoryItem>> = dao.getAllInventoryItems()
    val allTransactions: Flow<List<StockTransaction>> = dao.getAllTransactions()
    val allReconciliationSessions: Flow<List<ReconciliationSession>> = dao.getAllReconciliationSessions()

    suspend fun getItemByBarcode(barcode: String): InventoryItem? {
        return dao.getInventoryItemByBarcode(barcode)
    }

    suspend fun getItemById(id: Long): InventoryItem? {
        return dao.getInventoryItemById(id)
    }

    suspend fun saveItemWithTransaction(
        item: InventoryItem,
        transaction: StockTransaction?
    ): Long = dao.saveInventoryItemAndTransaction(item, transaction)

    suspend fun applyBarcodeStockChange(
        barcode: String,
        requestedChange: Int,
        transactionType: String,
        note: String
    ): Pair<InventoryItem, Int>? = dao.applyBarcodeStockChange(barcode, requestedChange, transactionType, note)

    suspend fun deleteItem(item: InventoryItem) = dao.deleteInventoryItemAndRecord(item)

    suspend fun deleteAllItems() {
        dao.deleteAllInventoryItemsAndRecord()
    }

    suspend fun addTransaction(transaction: StockTransaction): Long {
        return dao.insertTransaction(transaction)
    }

    fun getTransactionsForItem(id: Long): Flow<List<StockTransaction>> {
        return dao.getTransactionsForItem(id)
    }

    suspend fun createReconciliationSession(
        session: ReconciliationSession,
        items: List<ReconciliationItem>
    ): Long {
        return dao.createReconciliationSessionWithItems(session, items)
    }

    suspend fun getReconciliationSessionById(id: Long): ReconciliationSession? {
        return dao.getReconciliationSessionById(id)
    }

    fun getReconciliationItems(sessionId: Long): Flow<List<ReconciliationItem>> {
        return dao.getReconciliationItemsForSession(sessionId)
    }

    suspend fun getReconciliationItemsSync(sessionId: Long): List<ReconciliationItem> {
        return dao.getReconciliationItemsForSessionSync(sessionId)
    }

    suspend fun updateReconciliationItem(item: ReconciliationItem) {
        dao.updateReconciliationItemAndSessionStats(item)
    }

    suspend fun deleteReconciliationSession(sessionId: Long) {
        dao.deleteReconciliationSessionWithItems(sessionId)
    }
}
