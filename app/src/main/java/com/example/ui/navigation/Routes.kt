package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Customers : AppRoute
    @Serializable data class CustomerDetail(val customerId: String) : AppRoute
    @Serializable data object Debts : AppRoute
    @Serializable data class DebtDetail(val debtId: String) : AppRoute
    @Serializable data object Payments : AppRoute
    @Serializable data class PaymentDetail(val paymentId: String) : AppRoute
    @Serializable data object More : AppRoute
    @Serializable data object Products : AppRoute
    @Serializable data object Invoices : AppRoute
    @Serializable data object Reports : AppRoute
    @Serializable data object Reminders : AppRoute
    @Serializable data object Backup : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object AuditLog : AppRoute
}
