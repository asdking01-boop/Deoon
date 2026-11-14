package com.example

import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyUtilsUnitTest {

    @Test
    fun `parse and convert usd to ils with exact bigdecimal`() {
        val paymentMinor = MoneyUtils.parseToMinorUnits("100.00")!!
        assertEquals(10_000L, paymentMinor)

        val convertedIlsMinor = MoneyUtils.convertMinor(
            amountMinor = paymentMinor,
            from = CurrencyCode.USD,
            to = CurrencyCode.ILS,
            usdToIlsRate = BigDecimal("3.60")
        )
        assertEquals(36_000L, convertedIlsMinor)
        assertEquals("360.00 ₪", MoneyUtils.format(convertedIlsMinor, CurrencyCode.ILS, isArabic = true))
    }

    @Test
    fun `reject negative and zero amounts when not allowed`() {
        assertNull(MoneyUtils.parseToMinorUnits("-50"))
        assertNull(MoneyUtils.parseToMinorUnits("0"))
        assertEquals(0L, MoneyUtils.parseToMinorUnits("0", allowZero = true))
    }

    @Test
    fun `parse arabic digits cleanly`() {
        assertEquals(125_50L, MoneyUtils.parseToMinorUnits("١٢٥٫٥٠"))
    }
}
