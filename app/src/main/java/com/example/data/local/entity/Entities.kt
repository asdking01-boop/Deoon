package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.money.CurrencyCode
import kotlinx.serialization.Serializable

@Serializable
enum class DebtStatus {
    OPEN,
    PARTIAL,
    PAID,
    CANCELED
}

@Serializable
enum class PaymentMethod {
    CASH,
    BANK_TRANSFER,
    OTHER
}

@Serializable
enum class PaymentStatus {
    COMPLETED,
    REVERSED
}

@Serializable
enum class ReminderStatus {
    SCHEDULED,
    SENT,
    DISMISSED
}

@Serializable
enum class ReminderChannel {
    LOCAL_NOTIFICATION,
    WHATSAPP_SHARE
}

@Serializable
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val address: String = "",
    val photoUri: String? = null,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
@Entity(
    tableName = "products",
    indices = [Index(value = ["codeOrBarcode"])]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val codeOrBarcode: String = "",
    val purchasePriceMinor: Long,
    val sellingPriceMinor: Long,
    val currency: CurrencyCode,
    val availableQty: Int,
    val lowStockThreshold: Int = 5,
    val category: String = "",
    val imageUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["dueDate"]),
        Index(value = ["status"])
    ]
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val customerId: String,
    val currency: CurrencyCode,
    val subtotalMinor: Long,
    val discountMinor: Long = 0L,
    val taxMinor: Long = 0L,
    val totalAmountMinor: Long,
    val paidAmountMinor: Long = 0L,
    val status: DebtStatus = DebtStatus.OPEN,
    val dueDate: Long? = null,
    val notes: String = "",
    val receiptImageUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val remainingAmountMinor: Long
        get() = if (status == DebtStatus.CANCELED) 0L else (totalAmountMinor - paidAmountMinor).coerceAtLeast(0L)
}

@Serializable
@Entity(
    tableName = "debt_line_items",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["debtId"]), Index(value = ["productId"])]
)
data class DebtLineItemEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val productId: String? = null,
    val productName: String,
    val purchasePriceMinor: Long = 0L,
    val unitPriceMinor: Long,
    val quantity: Int,
    val lineTotalMinor: Long
)

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["debtId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["paymentDate"])
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val receiptNumber: String,
    val customerId: String,
    val debtId: String? = null,
    val paymentAmountMinor: Long,
    val paymentCurrency: CurrencyCode,
    val targetCurrency: CurrencyCode,
    val exchangeRateUsed: String,
    val convertedAmountMinor: Long,
    val appliedToDebtsMinor: Long = 0L,
    val overpaymentCreditMinor: Long = 0L,
    val paymentMethod: PaymentMethod,
    val paymentDate: Long,
    val notes: String = "",
    val status: PaymentStatus = PaymentStatus.COMPLETED,
    val idempotencyKey: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["paymentId"]), Index(value = ["debtId"])]
)
data class PaymentAllocationEntity(
    @PrimaryKey val id: String,
    val paymentId: String,
    val debtId: String,
    val allocatedMinor: Long
)

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"]), Index(value = ["dueAt"])]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val debtId: String? = null,
    val title: String,
    val message: String,
    val dueAt: Long,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val channel: ReminderChannel = ReminderChannel.LOCAL_NOTIFICATION,
    val createdAt: Long
)

@Serializable
@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["entityId"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val summary: String,
    val details: String = "",
    val timestamp: Long
)
