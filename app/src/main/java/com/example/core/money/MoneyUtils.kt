package com.example.core.money

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Serializable
enum class CurrencyCode(
    val code: String,
    val symbol: String,
    val nameAr: String,
    val nameEn: String
) {
    ILS("ILS", "₪", "شيكل", "Israeli New Shekel"),
    USD("USD", "$", "دولار", "US Dollar");

    fun label(isArabic: Boolean): String = if (isArabic) "$nameAr ($symbol)" else "$code ($symbol)"

    companion object {
        fun fromCode(code: String?): CurrencyCode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ILS
    }
}

object MoneyUtils {
    private val HUNDRED = BigDecimal("100")

    /**
     * Parses a user-entered monetary string into exact minor units (agorot / cents).
     * Returns null if malformed or negative (unless allowZero = false and value <= 0).
     */
    fun parseToMinorUnits(raw: String, allowZero: Boolean = false): Long? {
        val normalized = raw.trim()
            .replace("٫", ".")
            .replace(",", "")
            .replace("٬", "")
            .let { convertArabicDigitsToLatin(it) }
        if (normalized.isEmpty()) return null
        val bd = normalized.toBigDecimalOrNull() ?: return null
        if (bd < BigDecimal.ZERO) return null
        if (!allowZero && bd.compareTo(BigDecimal.ZERO) == 0) return null
        return bd.setScale(2, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .longValueExactOrNull()
    }

    fun parsePositiveRate(raw: String): BigDecimal? {
        val normalized = raw.trim()
            .replace("٫", ".")
            .replace(",", "")
            .let { convertArabicDigitsToLatin(it) }
        val bd = normalized.toBigDecimalOrNull() ?: return null
        return if (bd > BigDecimal.ZERO) bd.setScale(4, RoundingMode.HALF_UP) else null
    }

    fun minorToBigDecimal(minor: Long): BigDecimal =
        BigDecimal.valueOf(minor).divide(HUNDRED, 2, RoundingMode.HALF_UP)

    fun bigDecimalToMinor(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.HALF_UP).multiply(HUNDRED).longValueExact()

    /**
     * Exact BigDecimal multi-currency conversion.
     * usdToIlsRate is how many ILS equal 1 USD (e.g., 3.60).
     */
    fun convertMinor(
        amountMinor: Long,
        from: CurrencyCode,
        to: CurrencyCode,
        usdToIlsRate: BigDecimal
    ): Long {
        if (from == to) return amountMinor
        require(usdToIlsRate > BigDecimal.ZERO) { "Exchange rate must be positive" }
        val amount = minorToBigDecimal(amountMinor)
        val converted = when {
            from == CurrencyCode.USD && to == CurrencyCode.ILS ->
                amount.multiply(usdToIlsRate)
            from == CurrencyCode.ILS && to == CurrencyCode.USD ->
                amount.divide(usdToIlsRate, 4, RoundingMode.HALF_UP)
            else -> amount
        }
        return bigDecimalToMinor(converted)
    }

    fun format(amountMinor: Long, currency: CurrencyCode, isArabic: Boolean = true): String {
        val formatted = formatNumber(amountMinor)
        return if (isArabic) {
            "$formatted ${currency.symbol}"
        } else {
            "${currency.symbol}$formatted"
        }
    }

    fun formatNumber(amountMinor: Long): String {
        val bd = minorToBigDecimal(amountMinor)
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#,##0.00", symbols)
        return df.format(bd)
    }

    fun formatEditable(amountMinor: Long): String {
        return minorToBigDecimal(amountMinor).toPlainString()
    }

    private fun BigDecimal.longValueExactOrNull(): Long? = try {
        this.longValueExact()
    } catch (_: ArithmeticException) {
        null
    }

    fun convertArabicDigitsToLatin(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
