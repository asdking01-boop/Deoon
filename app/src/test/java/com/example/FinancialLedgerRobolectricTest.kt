package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.money.CurrencyCode
import com.example.data.local.DebtBookDatabase
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ProductEntity
import com.example.data.repository.LedgerRepository
import com.example.domain.model.DraftLineItem
import com.example.domain.model.PaymentSubmissionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinancialLedgerRobolectricTest {

    private lateinit var db: DebtBookDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DebtBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `cross currency payment 100 usd at 3_60 reduces 1000 ils debt to 640 ils and reversal restores 1000 ils`() = runTest {
        val customer = repo.saveCustomer(
            fullName = "أحمد",
            phone = "0599000001",
            address = "رام الله",
            photoUri = null,
            notes = ""
        )

        val debt = repo.createDebt(
            customerId = customer.id,
            currency = CurrencyCode.ILS,
            manualAmountMinor = 100_000L, // 1,000.00 ILS
            lineItems = emptyList(),
            discountMinor = 0L,
            taxMinor = 0L,
            dueDate = null,
            notes = "بضاعة",
            receiptImageUri = null
        )

        val res = repo.recordPayment(
            customerId = customer.id,
            debtId = debt.id,
            paymentAmountMinor = 10_000L, // 100.00 USD
            paymentCurrency = CurrencyCode.USD,
            targetCurrency = CurrencyCode.ILS,
            exchangeRate = BigDecimal("3.60"),
            paymentMethod = PaymentMethod.CASH,
            paymentDate = System.currentTimeMillis(),
            notes = "",
            idempotencyKey = "idem-1",
            allowOverpaymentAsCredit = false
        )

        assertTrue(res is PaymentSubmissionResult.Success)
        val payment = (res as PaymentSubmissionResult.Success).payment
        assertEquals(36_000L, payment.convertedAmountMinor) // 360.00 ILS

        val updatedDebt = repo.observeDebt(debt.id).first()!!
        assertEquals(36_000L, updatedDebt.paidAmountMinor)
        assertEquals(64_000L, updatedDebt.remainingAmountMinor) // 640.00 ILS
        assertEquals(DebtStatus.PARTIAL, updatedDebt.status)

        // Duplicate idempotency key prevented
        val dup = repo.recordPayment(
            customerId = customer.id,
            debtId = debt.id,
            paymentAmountMinor = 10_000L,
            paymentCurrency = CurrencyCode.USD,
            targetCurrency = CurrencyCode.ILS,
            exchangeRate = BigDecimal("3.60"),
            paymentMethod = PaymentMethod.CASH,
            paymentDate = System.currentTimeMillis(),
            notes = "",
            idempotencyKey = "idem-1",
            allowOverpaymentAsCredit = false
        )
        assertEquals(PaymentSubmissionResult.DuplicatePrevented, dup)

        // Reverse payment -> balances safely restored, payment record preserved as REVERSED
        val reversed = repo.reversePayment(payment.id, "خطأ إدخال")
        assertTrue(reversed)

        val afterReverseDebt = repo.observeDebt(debt.id).first()!!
        assertEquals(0L, afterReverseDebt.paidAmountMinor)
        assertEquals(100_000L, afterReverseDebt.remainingAmountMinor)
        assertEquals(DebtStatus.OPEN, afterReverseDebt.status)

        val afterReversePayment = repo.observePayment(payment.id).first()!!
        assertEquals(PaymentStatus.REVERSED, afterReversePayment.status)
    }

    @Test
    fun `debt invoice deducts inventory on creation and restores on cancellation`() = runTest {
        val customer = repo.saveCustomer(null, "سامي", "0599111222", "", null, "")
        val product = ProductEntity(
            id = "prod-1",
            name = "زيت زيتون",
            codeOrBarcode = "OLV-1",
            purchasePriceMinor = 3_000L,
            sellingPriceMinor = 4_500L,
            currency = CurrencyCode.ILS,
            availableQty = 10,
            createdAt = 1L,
            updatedAt = 1L
        )
        repo.saveProduct(product)

        val debt = repo.createDebt(
            customerId = customer.id,
            currency = CurrencyCode.ILS,
            manualAmountMinor = null,
            lineItems = listOf(DraftLineItem(product.id, product.name, product.purchasePriceMinor, product.sellingPriceMinor, 3)),
            discountMinor = 0L,
            taxMinor = 0L,
            dueDate = null,
            notes = "",
            receiptImageUri = null
        )

        val prodAfterCreate = repo.productsFlow.first().first { it.id == "prod-1" }
        assertEquals(7, prodAfterCreate.availableQty)

        val canceled = repo.cancelDebt(debt.id)
        assertTrue(canceled)

        val prodAfterCancel = repo.productsFlow.first().first { it.id == "prod-1" }
        assertEquals(10, prodAfterCancel.availableQty)
    }
}
