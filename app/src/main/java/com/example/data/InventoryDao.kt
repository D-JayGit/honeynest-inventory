package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    // Inventory Items
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode OR sku = :barcode LIMIT 1")
    suspend fun getInventoryItemByBarcode(barcode: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE sku = :sku LIMIT 1")
    suspend fun getInventoryItemBySku(sku: String): InventoryItem?

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
    @Query("SELECT * FROM billing_sessions WHERE status = 'OPEN' ORDER BY updatedAt DESC")
    fun observeOpenBillingSessions(): Flow<List<BillingSessionWithItems>>

    @Transaction
    @Query("SELECT * FROM billing_sessions WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeCompletedBillingSessions(): Flow<List<BillingSessionWithItems>>

    @Query("SELECT * FROM billing_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getBillingSession(sessionId: Long): BillingSession?

    @Query("SELECT * FROM billing_session_items WHERE sessionId = :sessionId AND itemId = :itemId LIMIT 1")
    suspend fun getBillingSessionItem(sessionId: Long, itemId: Long): BillingSessionItem?

    @Insert
    suspend fun insertBillingSession(session: BillingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBillingSessionItem(item: BillingSessionItem)

    @Query("UPDATE billing_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchBillingSession(sessionId: Long, updatedAt: Long)

    @Query("UPDATE billing_sessions SET discountPercentage = :legacyPercentage, discountType = :discountType, discountValue = :discountValue, updatedAt = :updatedAt WHERE id = :sessionId AND status = 'OPEN'")
    suspend fun updateBillingSessionDiscount(sessionId: Long, legacyPercentage: Double, discountType: String, discountValue: Double, updatedAt: Long): Int

    @Transaction
    suspend fun setBillingSessionDiscount(sessionId: Long, discountType: String, discountValue: Double): String? {
        if (discountType !in listOf("PERCENTAGE", "AMOUNT")) return "Choose percentage or amount discount."
        if (!discountValue.isFinite()) return "Enter a valid discount value."
        if (discountValue < 0.0) return "Discount cannot be negative."
        if (discountType == "PERCENTAGE" && discountValue > 100.0) return "Percentage discount cannot exceed 100%."
        val sessionItems = getBillingSessionItems(sessionId)
        val subtotal = sessionItems.sumOf { it.unitPrice * it.quantity }
        if (discountType == "AMOUNT" && discountValue > subtotal) return "Amount discount cannot exceed the bill subtotal."
        val legacyPercentage = if (discountType == "PERCENTAGE") discountValue else 0.0
        val updated = updateBillingSessionDiscount(
            sessionId,
            legacyPercentage,
            discountType,
            discountValue,
            System.currentTimeMillis()
        )
        return if (updated == 0) "This customer bill is no longer available." else null
    }

    @Query("DELETE FROM billing_session_items WHERE sessionId = :sessionId AND itemId = :itemId")
    suspend fun deleteBillingSessionItem(sessionId: Long, itemId: Long)

    @Query("DELETE FROM billing_sessions WHERE id = :sessionId AND status = 'OPEN'")
    suspend fun deleteOpenBillingSession(sessionId: Long)

    @Transaction
    suspend fun addItemToBillingSession(sessionId: Long, itemId: Long, quantity: Int): String? {
        if (quantity <= 0) return "Enter a quantity greater than zero."
        val session = getBillingSession(sessionId)
            ?: return "This customer bill is no longer available."
        if (session.status != "OPEN") return "This customer bill is already complete."
        val product = getInventoryItemById(itemId)
            ?: return "This product is no longer in inventory."
        val existing = getBillingSessionItem(sessionId, itemId)
        val updatedQuantity = (existing?.quantity ?: 0) + quantity
        if (updatedQuantity > product.currentQuantity) {
            return "Only ${product.currentQuantity} units of ${product.name} are in stock."
        }
        saveBillingSessionItem(
            existing?.copy(quantity = updatedQuantity) ?: BillingSessionItem(
                sessionId = sessionId,
                itemId = product.id,
                productName = product.name,
                barcode = product.barcode,
                sku = product.sku,
                unitPrice = product.sellingPrice,
                unitCost = product.purchasePrice,
                quantity = updatedQuantity
            )
        )
        touchBillingSession(sessionId, System.currentTimeMillis())
        return null
    }

    @Transaction
    suspend fun setBillingSessionItemQuantity(sessionId: Long, itemId: Long, quantity: Int): String? {
        val session = getBillingSession(sessionId)
            ?: return "This customer bill is no longer available."
        if (session.status != "OPEN") return "This customer bill is already complete."
        if (quantity <= 0) {
            deleteBillingSessionItem(sessionId, itemId)
            touchBillingSession(sessionId, System.currentTimeMillis())
            return null
        }
        val existing = getBillingSessionItem(sessionId, itemId)
            ?: return "This product is no longer on the bill."
        val product = getInventoryItemById(itemId)
            ?: return "This product is no longer in inventory."
        if (quantity > product.currentQuantity) {
            return "Only ${product.currentQuantity} units of ${product.name} are in stock."
        }
        saveBillingSessionItem(existing.copy(quantity = quantity))
        touchBillingSession(sessionId, System.currentTimeMillis())
        return null
    }

    @Transaction
    suspend fun completeBillingSession(sessionId: Long): String? {
        val session = getBillingSession(sessionId)
            ?: return "This customer bill is no longer available."
        if (session.status != "OPEN") return "This customer bill is already complete."
        val lines = getBillingSessionItems(sessionId)
        if (lines.isEmpty()) return "Add at least one product before completing the purchase."

        val currentItems = lines.map { line -> line to getInventoryItemById(line.itemId) }
        val missingItem = currentItems.firstOrNull { it.second == null }
        if (missingItem != null) return "${missingItem.first.productName} is no longer in inventory. Remove it from the bill to continue."
        val insufficientItem = currentItems.firstOrNull { (line, item) -> item!!.currentQuantity < line.quantity }
        if (insufficientItem != null) {
            return "Only ${insufficientItem.second!!.currentQuantity} units of ${insufficientItem.first.productName} remain in stock. Update the bill to continue."
        }

        currentItems.forEach { (line, currentItem) ->
            val item = currentItem!!
            updateInventoryItem(
                item.copy(
                    currentQuantity = item.currentQuantity - line.quantity,
                    updatedAt = System.currentTimeMillis()
                )
            )
            insertTransaction(
                StockTransaction(
                    itemId = item.id,
                    barcode = item.barcode,
                    itemName = item.name,
                    changeQuantity = -line.quantity,
                    transactionType = "SALE",
                    note = "Customer purchase: ${session.customerName}"
                )
            )
        }
        markBillingSessionComplete(sessionId, System.currentTimeMillis())
        return null
    }

    @Query("SELECT * FROM billing_session_items WHERE sessionId = :sessionId")
    suspend fun getBillingSessionItems(sessionId: Long): List<BillingSessionItem>

    @Query("UPDATE billing_sessions SET status = 'COMPLETED', updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun markBillingSessionComplete(sessionId: Long, updatedAt: Long)

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
        val savedItem = if (item.id == 0L) {
            val itemId = insertInventoryItem(item.copy(sku = null))
            val sku = SkuGenerator.create(item.productCode, item.colorCode, item.sizeCode, itemId)
            item.copy(
                id = itemId,
                sku = sku,
                barcode = item.barcode.trim().ifBlank { sku }
            ).also { updateInventoryItem(it) }
        } else {
            val sku = item.sku ?: SkuGenerator.create(item.productCode, item.colorCode, item.sizeCode, item.id)
            item.copy(
                sku = sku,
                barcode = item.barcode.trim().ifBlank { sku }
            ).also { updateInventoryItem(it) }
        }
        transaction?.let {
            insertTransaction(it.copy(itemId = savedItem.id, barcode = savedItem.barcode))
        }
        return savedItem.id
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
    suspend fun completeSale(itemQuantities: Map<Long, Int>): List<InventoryItem>? {
        if (itemQuantities.isEmpty() || itemQuantities.values.any { it <= 0 }) return null

        val items = itemQuantities.mapNotNull { (itemId, quantity) ->
            getInventoryItemById(itemId)?.let { item -> item to quantity }
        }
        if (items.size != itemQuantities.size || items.any { (item, quantity) -> item.currentQuantity < quantity }) {
            return null
        }

        return items.map { (item, quantity) ->
            item.copy(
                currentQuantity = item.currentQuantity - quantity,
                updatedAt = System.currentTimeMillis()
            ).also { updatedItem ->
                updateInventoryItem(updatedItem)
                insertTransaction(
                    StockTransaction(
                        itemId = item.id,
                        barcode = item.barcode,
                        itemName = item.name,
                        changeQuantity = -quantity,
                        transactionType = "SALE",
                        note = "Customer purchase"
                    )
                )
            }
        }
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
