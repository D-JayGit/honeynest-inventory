package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.ColumnInfo

@Entity(tableName = "billing_sessions")
data class BillingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "OPEN",
    // Kept for existing database compatibility; new billing logic uses discountType and discountValue.
    @ColumnInfo(defaultValue = "0.0") val discountPercentage: Double = 0.0,
    @ColumnInfo(defaultValue = "'PERCENTAGE'") val discountType: String = "PERCENTAGE",
    @ColumnInfo(defaultValue = "0.0") val discountValue: Double = 0.0
)

@Entity(
    tableName = "billing_session_items",
    primaryKeys = ["sessionId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = BillingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BillingSessionItem(
    val sessionId: Long,
    val itemId: Long,
    val productName: String,
    val barcode: String,
    val sku: String?,
    val unitPrice: Double,
    @ColumnInfo(defaultValue = "0.0") val unitCost: Double = 0.0,
    val quantity: Int
)

data class BillingSessionWithItems(
    @Embedded val session: BillingSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val items: List<BillingSessionItem>
)
