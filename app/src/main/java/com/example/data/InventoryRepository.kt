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

    suspend fun insertItem(item: InventoryItem): Long {
        return dao.insertInventoryItem(item)
    }

    suspend fun updateItem(item: InventoryItem) {
        dao.updateInventoryItem(item)
    }

    suspend fun deleteItem(item: InventoryItem) {
        dao.deleteInventoryItem(item)
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
        val sessionId = dao.insertReconciliationSession(session)
        val itemsWithSessionId = items.map { it.copy(sessionId = sessionId) }
        dao.insertReconciliationItems(itemsWithSessionId)
        return sessionId
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
        dao.updateReconciliationItem(item)
        
        // Update session stats
        val sessionId = item.sessionId
        val allSessionItems = dao.getReconciliationItemsForSessionSync(sessionId)
        val verifiedCount = allSessionItems.count { it.isVerified }
        
        val session = dao.getReconciliationSessionById(sessionId)
        if (session != null) {
            dao.updateReconciliationSession(
                session.copy(
                    verifiedItemsCount = verifiedCount,
                    status = if (verifiedCount == allSessionItems.size) "COMPLETED" else "OPEN"
                )
            )
        }
    }

    suspend fun deleteReconciliationSession(sessionId: Long) {
        dao.deleteReconciliationSession(sessionId)
    }
}
