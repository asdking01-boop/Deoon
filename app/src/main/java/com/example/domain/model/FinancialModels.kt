package com.example.domain.model

import com.example.core.money.CurrencyCode
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentStatus
import java.math.BigDecimal

data class CustomerFinancialSummary(
    val customer: CustomerEntity,
    val outstandingIlsMinor: Long,
    val outstandingUsdMinor: Long,
    val paidIlsMinor: Long,
    val paidUsdMinor: Long,
    val creditIlsMinor: Long,
    val creditUsdMinor: Long,
    val openDebtsCount: Int,
    val overdueDebtsCount: Int,
    val latestActivityAt: Long
) {
    val totalOpenBalancesCount: Int get() = openDebtsCount
    val hasAnyDebt: Boolean get() = outstandingIlsMinor > 0L || outstandingUsdMinor > 0L
}

data class DashboardSummary(
    val outstandingIlsMinor: Long = 0L,
    val outstandingUsdMinor: Long = 0L,
    val collectedIlsMinor: Long = 0L,
    val collectedUsdMinor: Long = 0L,
    val overdueIlsMinor: Long = 0L,
    val overdueUsdMinor: Long = 0L,
    val overdueCount: Int = 0,
    val activeCustomersCount: Int = 0,
    val monthlyStats: List<MonthlyStatPoint> = emptyList()
)

data class MonthlyStatPoint(
    val monthLabel: String,
    val yearMonthKey: String,
    val debtsIlsMinor: Long,
    val collectedIlsMinor: Long,
    val debtsUsdMinor: Long,
    val collectedUsdMinor: Long
)

data class DraftLineItem(
    val productId: String? = null,
    val productName: String,
    val purchasePriceMinor: Long = 0L,
    val unitPriceMinor: Long,
    val quantity: Int
) {
    val lineTotalMinor: Long get() = unitPriceMinor * quantity.toLong()
}

sealed class PaymentSubmissionResult {
    data class Success(val payment: PaymentEntity) : PaymentSubmissionResult()
    data object DuplicatePrevented : PaymentSubmissionResult()
    data class OverpaymentNeedsConfirmation(
        val openRemainingMinor: Long,
        val excessCreditMinor: Long,
        val targetCurrency: CurrencyCode
    ) : PaymentSubmissionResult()
    data class ValidationError(val messageAr: String, val messageEn: String) : PaymentSubmissionResult()
}

data class BackupConflictInfo(
    val backupCreatedAt: Long,
    val backupCustomersCount: Int,
    val backupDebtsCount: Int,
    val backupPaymentsCount: Int,
    val backupProductsCount: Int,
    val localCustomersCount: Int,
    val localDebtsCount: Int,
    val localPaymentsCount: Int,
    val hasNewerLocalData: Boolean,
    val latestLocalTimestamp: Long
)

enum class RestoreMode {
    REPLACE_ALL,
    MERGE_KEEP_NEWER
}

fun DebtEntity.isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val due = dueDate ?: return false
    return (status == DebtStatus.OPEN || status == DebtStatus.PARTIAL) && due < nowMillis
}

fun BigDecimal.toRateString(): String = stripTrailingZeros().let {
    if (it.scale() < 2) it.setScale(2) else it
}.toPlainString()
