package com.example.data

import androidx.room.*

@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val currentQuantity: Int = 0,
    val threshold: Int = 5, // low stock threshold
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val listPrice: Double = 0.0, // used if we calculate selling price using discount
    val discountPercentage: Double = 0.0, // if > 0, sellingPrice = listPrice * (1 - discountPercentage/100)
    val updatedAt: Long = System.currentTimeMillis()
)
