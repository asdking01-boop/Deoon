package com.example.data.local

import androidx.room.TypeConverter
import com.example.core.money.CurrencyCode
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ReminderChannel
import com.example.data.local.entity.ReminderStatus

class RoomConverters {
    @TypeConverter
    fun fromCurrencyCode(value: CurrencyCode): String = value.name

    @TypeConverter
    fun toCurrencyCode(value: String): CurrencyCode = CurrencyCode.fromCode(value)

    @TypeConverter
    fun fromDebtStatus(value: DebtStatus): String = value.name

    @TypeConverter
    fun toDebtStatus(value: String): DebtStatus =
        DebtStatus.entries.firstOrNull { it.name == value } ?: DebtStatus.OPEN

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod =
        PaymentMethod.entries.firstOrNull { it.name == value } ?: PaymentMethod.CASH

    @TypeConverter
    fun fromPaymentStatus(value: PaymentStatus): String = value.name

    @TypeConverter
    fun toPaymentStatus(value: String): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name == value } ?: PaymentStatus.COMPLETED

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus =
        ReminderStatus.entries.firstOrNull { it.name == value } ?: ReminderStatus.SCHEDULED

    @TypeConverter
    fun fromReminderChannel(value: ReminderChannel): String = value.name

    @TypeConverter
    fun toReminderChannel(value: String): ReminderChannel =
        ReminderChannel.entries.firstOrNull { it.name == value } ?: ReminderChannel.LOCAL_NOTIFICATION
}
