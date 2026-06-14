package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val barcode: String,
    val itemName: String,
    val changeQuantity: Int, // e.g. +10 or -2
    val transactionType: String, // "SCAN_IN", "SCAN_OUT", "MANUAL_ADD", "MANUAL_SUB", "BILL_RECONCILE"
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)
