package com.example.ui.i18n

import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ReminderStatus

fun tr(isArabic: Boolean, ar: String, en: String): String = if (isArabic) ar else en

fun trVal(isArabic: Boolean, ar: String, en: String): String = if (isArabic) ar else en

fun DebtStatus.localized(isArabic: Boolean): String = when (this) {
    DebtStatus.OPEN -> if (isArabic) "مفتوح" else "Open"
    DebtStatus.PARTIAL -> if (isArabic) "مدفوع جزئياً" else "Partial"
    DebtStatus.PAID -> if (isArabic) "مسدد" else "Paid"
    DebtStatus.CANCELED -> if (isArabic) "ملغى" else "Canceled"
}

fun PaymentMethod.localized(isArabic: Boolean): String = when (this) {
    PaymentMethod.CASH -> if (isArabic) "نقداً" else "Cash"
    PaymentMethod.BANK_TRANSFER -> if (isArabic) "تحويل بنكي" else "Bank Transfer"
    PaymentMethod.OTHER -> if (isArabic) "أخرى" else "Other"
}

fun PaymentStatus.localized(isArabic: Boolean): String = when (this) {
    PaymentStatus.COMPLETED -> if (isArabic) "مكتملة" else "Completed"
    PaymentStatus.REVERSED -> if (isArabic) "معكوسة" else "Reversed"
}

fun ReminderStatus.localized(isArabic: Boolean): String = when (this) {
    ReminderStatus.SCHEDULED -> if (isArabic) "مجدول" else "Scheduled"
    ReminderStatus.SENT -> if (isArabic) "تم الإرسال" else "Sent"
    ReminderStatus.DISMISSED -> if (isArabic) "مغلق" else "Dismissed"
}
