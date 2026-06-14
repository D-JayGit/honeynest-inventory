package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reconciliation_sessions")
data class ReconciliationSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val billName: String,
    val status: String = "OPEN", // "OPEN", "COMPLETED"
    val totalBillItems: Int = 0,
    val verifiedItemsCount: Int = 0
)

@Entity(tableName = "reconciliation_items")
data class ReconciliationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0L,
    val barcode: String = "", // can map to products
    val name: String,
    val expectedQuantity: Int,
    val expectedPrice: Double,
    val actualScannedQuantity: Int = 0,
    val actualPriceMatch: Boolean = false,
    val isVerified: Boolean = false
)
