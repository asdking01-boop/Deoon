package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.DebtBookDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReminderChannel
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.domain.model.CustomerFinancialSummary
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DraftLineItem
import com.example.domain.model.MonthlyStatPoint
import com.example.domain.model.PaymentSubmissionResult
import com.example.domain.model.isOverdue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class LedgerRepository(private val db: DebtBookDatabase) {

    private val customerDao = db.customerDao()
    private val productDao = db.productDao()
    private val debtDao = db.debtDao()
    private val paymentDao = db.paymentDao()
    private val reminderDao = db.reminderDao()
    private val auditLogDao = db.auditLogDao()

    val customersFlow: Flow<List<CustomerEntity>> = customerDao.observeAll()
    val productsFlow: Flow<List<ProductEntity>> = productDao.observeAll()
    val debtsFlow: Flow<List<DebtEntity>> = debtDao.observeAll()
    val allLineItemsFlow: Flow<List<DebtLineItemEntity>> = debtDao.observeAllLineItems()
    val paymentsFlow: Flow<List<PaymentEntity>> = paymentDao.observeAll()
    val remindersFlow: Flow<List<ReminderEntity>> = reminderDao.observeAll()
    val auditLogsFlow: Flow<List<AuditLogEntity>> = auditLogDao.observeRecent()

    fun observeCustomer(customerId: String): Flow<CustomerEntity?> = customerDao.observeById(customerId)
    fun observeCustomerDebts(customerId: String): Flow<List<DebtEntity>> = debtDao.observeByCustomer(customerId)
    fun observeCustomerPayments(customerId: String): Flow<List<PaymentEntity>> = paymentDao.observeByCustomer(customerId)
    fun observeDebt(debtId: String): Flow<DebtEntity?> = debtDao.observeById(debtId)
    fun observeDebtLineItems(debtId: String): Flow<List<DebtLineItemEntity>> = debtDao.observeLineItems(debtId)
    fun observePayment(paymentId: String): Flow<PaymentEntity?> = paymentDao.observeById(paymentId)

    val customerSummariesFlow: Flow<List<CustomerFinancialSummary>> = combine(
        customersFlow,
        debtsFlow,
        paymentsFlow
    ) { customers, debts, payments ->
        val now = System.currentTimeMillis()
        val debtsByCustomer = debts.groupBy { it.customerId }
        val paymentsByCustomer = payments.groupBy { it.customerId }

        customers.map { customer ->
            val cDebts = debtsByCustomer[customer.id].orEmpty()
            val cPayments = paymentsByCustomer[customer.id].orEmpty()
                .filter { it.status == PaymentStatus.COMPLETED }

            val openIls = cDebts.filter {
                it.currency == CurrencyCode.ILS && (it.status == DebtStatus.OPEN || it.status == DebtStatus.PARTIAL)
            }.sumOf { it.remainingAmountMinor }

            val openUsd = cDebts.filter {
                it.currency == CurrencyCode.USD && (it.status == DebtStatus.OPEN || it.status == DebtStatus.PARTIAL)
            }.sumOf { it.remainingAmountMinor }

            val paidIls = cPayments.filter { it.targetCurrency == CurrencyCode.ILS }.sumOf { it.convertedAmountMinor }
            val paidUsd = cPayments.filter { it.targetCurrency == CurrencyCode.USD }.sumOf { it.convertedAmountMinor }

            val creditIls = cPayments.filter { it.targetCurrency == CurrencyCode.ILS }.sumOf { it.overpaymentCreditMinor }
            val creditUsd = cPayments.filter { it.targetCurrency == CurrencyCode.USD }.sumOf { it.overpaymentCreditMinor }

            val openCount = cDebts.count { it.status == DebtStatus.OPEN || it.status == DebtStatus.PARTIAL }
            val overdueCount = cDebts.count { it.isOverdue(now) }

            val latestDebt = cDebts.maxOfOrNull { it.updatedAt } ?: 0L
            val latestPay = cPayments.maxOfOrNull { it.paymentDate } ?: 0L
            val latest = maxOf(customer.updatedAt, latestDebt, latestPay)

            CustomerFinancialSummary(
                customer = customer,
                outstandingIlsMinor = openIls,
                outstandingUsdMinor = openUsd,
                paidIlsMinor = paidIls,
                paidUsdMinor = paidUsd,
                creditIlsMinor = creditIls,
                creditUsdMinor = creditUsd,
                openDebtsCount = openCount,
                overdueDebtsCount = overdueCount,
                latestActivityAt = latest
            )
        }
    }

    val dashboardSummaryFlow: Flow<DashboardSummary> = combine(
        customersFlow,
        debtsFlow,
        paymentsFlow
    ) { customers, debts, payments ->
        val now = System.currentTimeMillis()
        val activeDebts = debts.filter { it.status != DebtStatus.CANCELED }
        val completedPayments = payments.filter { it.status == PaymentStatus.COMPLETED }

        val outstandingIls = activeDebts.filter { it.currency == CurrencyCode.ILS }.sumOf { it.remainingAmountMinor }
        val outstandingUsd = activeDebts.filter { it.currency == CurrencyCode.USD }.sumOf { it.remainingAmountMinor }

        val collectedIls = completedPayments.filter { it.targetCurrency == CurrencyCode.ILS }.sumOf { it.convertedAmountMinor }
        val collectedUsd = completedPayments.filter { it.targetCurrency == CurrencyCode.USD }.sumOf { it.convertedAmountMinor }

        val overdueList = activeDebts.filter { it.isOverdue(now) }
        val overdueIls = overdueList.filter { it.currency == CurrencyCode.ILS }.sumOf { it.remainingAmountMinor }
        val overdueUsd = overdueList.filter { it.currency == CurrencyCode.USD }.sumOf { it.remainingAmountMinor }

        val activeCustomers = customers.count { !it.isArchived }

        // Build last 6 months stats
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFmt = SimpleDateFormat("MM/yy", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val months = (5 downTo 0).map { offset ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -offset)
            keyFmt.format(c.time) to labelFmt.format(c.time)
        }

        val debtsByMonth = activeDebts.groupBy { keyFmt.format(Date(it.createdAt)) }
        val paysByMonth = completedPayments.groupBy { keyFmt.format(Date(it.paymentDate)) }

        val monthlyStats = months.map { (key, label) ->
            val mDebts = debtsByMonth[key].orEmpty()
            val mPays = paysByMonth[key].orEmpty()
            MonthlyStatPoint(
                monthLabel = label,
                yearMonthKey = key,
                debtsIlsMinor = mDebts.filter { it.currency == CurrencyCode.ILS }.sumOf { it.totalAmountMinor },
                collectedIlsMinor = mPays.filter { it.targetCurrency == CurrencyCode.ILS }.sumOf { it.convertedAmountMinor },
                debtsUsdMinor = mDebts.filter { it.currency == CurrencyCode.USD }.sumOf { it.totalAmountMinor },
                collectedUsdMinor = mPays.filter { it.targetCurrency == CurrencyCode.USD }.sumOf { it.convertedAmountMinor }
            )
        }

        DashboardSummary(
            outstandingIlsMinor = outstandingIls,
            outstandingUsdMinor = outstandingUsd,
            collectedIlsMinor = collectedIls,
            collectedUsdMinor = collectedUsd,
            overdueIlsMinor = overdueIls,
            overdueUsdMinor = overdueUsd,
            overdueCount = overdueList.size,
            activeCustomersCount = activeCustomers,
            monthlyStats = monthlyStats
        )
    }

    suspend fun saveCustomer(
        id: String? = null,
        fullName: String,
        phone: String,
        address: String,
        photoUri: String?,
        notes: String
    ): CustomerEntity {
        val now = System.currentTimeMillis()
        val existing = id?.let { customerDao.getById(it) }
        val entity = CustomerEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            fullName = fullName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            photoUri = photoUri,
            notes = notes.trim(),
            isArchived = existing?.isArchived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        db.withTransaction {
            customerDao.upsert(entity)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "CUSTOMER",
                    entityId = entity.id,
                    action = if (existing == null) "CREATE" else "UPDATE",
                    summary = "${entity.fullName} (${entity.phone})",
                    timestamp = now
                )
            )
        }
        return entity
    }

    suspend fun setCustomerArchived(customerId: String, archived: Boolean) {
        val now = System.currentTimeMillis()
        val existing = customerDao.getById(customerId) ?: return
        db.withTransaction {
            customerDao.upsert(existing.copy(isArchived = archived, updatedAt = now))
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "CUSTOMER",
                    entityId = customerId,
                    action = if (archived) "ARCHIVE" else "UNARCHIVE",
                    summary = existing.fullName,
                    timestamp = now
                )
            )
        }
    }

    suspend fun deleteCustomerIfNoLedgerHistory(customerId: String): Boolean {
        val debts = debtDao.getAllOnce().any { it.customerId == customerId }
        val pays = paymentDao.getAllOnce().any { it.customerId == customerId }
        if (debts || pays) return false
        val existing = customerDao.getById(customerId) ?: return false
        db.withTransaction {
            customerDao.delete(existing)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "CUSTOMER",
                    entityId = customerId,
                    action = "DELETE",
                    summary = existing.fullName,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return true
    }

    suspend fun saveProduct(product: ProductEntity) {
        val now = System.currentTimeMillis()
        val existing = productDao.getById(product.id)
        val entity = product.copy(
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        db.withTransaction {
            productDao.upsert(entity)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PRODUCT",
                    entityId = entity.id,
                    action = if (existing == null) "CREATE" else "UPDATE",
                    summary = "${entity.name} (qty=${entity.availableQty})",
                    timestamp = now
                )
            )
        }
    }

    suspend fun importProducts(products: List<ProductEntity>) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            productDao.upsertAll(products)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PRODUCT",
                    entityId = "bulk_import",
                    action = "IMPORT",
                    summary = "Imported ${products.size} products",
                    timestamp = now
                )
            )
        }
    }

    suspend fun deleteProduct(product: ProductEntity) {
        db.withTransaction {
            productDao.delete(product)
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PRODUCT",
                    entityId = product.id,
                    action = "DELETE",
                    summary = product.name,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Atomically creates a Debt Invoice, saves line items, and deducts product inventory ONLY upon finalization.
     */
    suspend fun createDebt(
        customerId: String,
        currency: CurrencyCode,
        manualAmountMinor: Long?,
        lineItems: List<DraftLineItem>,
        discountMinor: Long,
        taxMinor: Long,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?
    ): DebtEntity {
        val now = System.currentTimeMillis()
        val subtotalMinor = if (lineItems.isNotEmpty()) {
            lineItems.sumOf { it.lineTotalMinor }
        } else {
            manualAmountMinor ?: 0L
        }
        val totalMinor = (subtotalMinor - discountMinor + taxMinor)
        require(totalMinor > 0L) { "Debt total must be positive" }

        return db.withTransaction {
            val year = SimpleDateFormat("yyyy", Locale.US).format(Date(now))
            val seq = debtDao.countAll() + 1
            val invoiceNumber = "INV-$year-${seq.toString().padStart(4, '0')}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
            val debtId = UUID.randomUUID().toString()

            // Deduct stock atomically for catalog products
            for (item in lineItems) {
                val prodId = item.productId
                if (prodId != null) {
                    val updatedRows = productDao.decrementStockIfAvailable(prodId, item.quantity, now)
                    if (updatedRows == 0) {
                        throw IllegalStateException("Insufficient stock for product: ${item.productName}")
                    }
                }
            }

            val debt = DebtEntity(
                id = debtId,
                invoiceNumber = invoiceNumber,
                customerId = customerId,
                currency = currency,
                subtotalMinor = subtotalMinor,
                discountMinor = discountMinor,
                taxMinor = taxMinor,
                totalAmountMinor = totalMinor,
                paidAmountMinor = 0L,
                status = DebtStatus.OPEN,
                dueDate = dueDate,
                notes = notes.trim(),
                receiptImageUri = receiptImageUri,
                createdAt = now,
                updatedAt = now
            )
            debtDao.upsert(debt)

            if (lineItems.isNotEmpty()) {
                debtDao.insertLineItems(
                    lineItems.map {
                        DebtLineItemEntity(
                            id = UUID.randomUUID().toString(),
                            debtId = debtId,
                            productId = it.productId,
                            productName = it.productName,
                            purchasePriceMinor = it.purchasePriceMinor,
                            unitPriceMinor = it.unitPriceMinor,
                            quantity = it.quantity,
                            lineTotalMinor = it.lineTotalMinor
                        )
                    }
                )
            }

            // Auto-create a scheduled reminder if dueDate is set
            if (dueDate != null) {
                val customer = customerDao.getById(customerId)
                reminderDao.upsert(
                    ReminderEntity(
                        id = UUID.randomUUID().toString(),
                        customerId = customerId,
                        debtId = debtId,
                        title = "استحقاق فاتورة ${invoiceNumber}",
                        message = "تذكير بدفعة مستحقة على ${customer?.fullName ?: ""} بقيمة ${MoneyUtils.format(totalMinor, currency, true)}",
                        dueAt = dueDate,
                        status = ReminderStatus.SCHEDULED,
                        channel = ReminderChannel.LOCAL_NOTIFICATION,
                        createdAt = now
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "DEBT",
                    entityId = debtId,
                    action = "CREATE",
                    summary = "$invoiceNumber -> ${MoneyUtils.format(totalMinor, currency, false)}",
                    timestamp = now
                )
            )
            debt
        }
    }

    suspend fun updateDebtDetails(
        debtId: String,
        newManualTotalMinor: Long?,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?
    ): Boolean {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = debtDao.getById(debtId) ?: return@withTransaction false
            if (existing.status == DebtStatus.CANCELED) return@withTransaction false
            val lineItems = debtDao.getLineItems(debtId)

            val updatedTotal = if (lineItems.isEmpty() && newManualTotalMinor != null && newManualTotalMinor >= existing.paidAmountMinor && newManualTotalMinor > 0L) {
                newManualTotalMinor
            } else {
                existing.totalAmountMinor
            }
            val updatedSubtotal = if (lineItems.isEmpty() && updatedTotal != existing.totalAmountMinor) {
                updatedTotal + existing.discountMinor - existing.taxMinor
            } else {
                existing.subtotalMinor
            }
            val newStatus = when {
                existing.paidAmountMinor >= updatedTotal -> DebtStatus.PAID
                existing.paidAmountMinor > 0L -> DebtStatus.PARTIAL
                else -> DebtStatus.OPEN
            }

            debtDao.update(
                existing.copy(
                    subtotalMinor = updatedSubtotal,
                    totalAmountMinor = updatedTotal,
                    status = newStatus,
                    dueDate = dueDate,
                    notes = notes.trim(),
                    receiptImageUri = receiptImageUri,
                    updatedAt = now
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "DEBT",
                    entityId = debtId,
                    action = "UPDATE",
                    summary = "${existing.invoiceNumber} updated",
                    timestamp = now
                )
            )
            true
        }
    }

    /**
     * Cancels a debt entry ONLY if it has no unreversed paid amounts, and atomically restores inventory.
     */
    suspend fun cancelDebt(debtId: String): Boolean {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = debtDao.getById(debtId) ?: return@withTransaction false
            if (existing.status == DebtStatus.CANCELED) return@withTransaction false
            if (existing.paidAmountMinor > 0L) {
                throw IllegalStateException("Cannot cancel debt with active payments. Reverse payments first.")
            }

            val items = debtDao.getLineItems(debtId)
            for (item in items) {
                val prodId = item.productId
                if (prodId != null) {
                    productDao.incrementStock(prodId, item.quantity, now)
                }
            }

            debtDao.update(existing.copy(status = DebtStatus.CANCELED, updatedAt = now))
            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "DEBT",
                    entityId = debtId,
                    action = "CANCEL",
                    summary = "${existing.invoiceNumber} canceled & stock restored",
                    timestamp = now
                )
            )
            true
        }
    }

    /**
     * Atomically records a payment with historical exchange rate preservation, idempotency check,
     * FIFO or targeted allocation, and explicit overpayment-as-credit gating.
     */
    suspend fun recordPayment(
        customerId: String,
        debtId: String?,
        paymentAmountMinor: Long,
        paymentCurrency: CurrencyCode,
        targetCurrency: CurrencyCode,
        exchangeRate: BigDecimal,
        paymentMethod: PaymentMethod,
        paymentDate: Long,
        notes: String,
        idempotencyKey: String,
        allowOverpaymentAsCredit: Boolean = false
    ): PaymentSubmissionResult {
        if (paymentAmountMinor <= 0L) {
            return PaymentSubmissionResult.ValidationError(
                messageAr = "مبلغ الدفعة يجب أن يكون أكبر من صفر",
                messageEn = "Payment amount must be greater than zero"
            )
        }
        if (exchangeRate <= BigDecimal.ZERO) {
            return PaymentSubmissionResult.ValidationError(
                messageAr = "سعر الصرف يجب أن يكون أكبر من صفر",
                messageEn = "Exchange rate must be greater than zero"
            )
        }

        val now = System.currentTimeMillis()
        return db.withTransaction {
            if (paymentDao.getByIdempotencyKey(idempotencyKey) != null) {
                return@withTransaction PaymentSubmissionResult.DuplicatePrevented
            }

            val convertedMinor = MoneyUtils.convertMinor(
                amountMinor = paymentAmountMinor,
                from = paymentCurrency,
                to = targetCurrency,
                usdToIlsRate = exchangeRate
            )

            val candidateDebts: List<DebtEntity> = if (debtId != null) {
                val target = debtDao.getById(debtId)
                    ?: return@withTransaction PaymentSubmissionResult.ValidationError(
                        messageAr = "الدين المحدد غير موجود",
                        messageEn = "Selected debt not found"
                    )
                if (target.currency != targetCurrency) {
                    return@withTransaction PaymentSubmissionResult.ValidationError(
                        messageAr = "عملة الحساب المستهدفة لا تطابق عملة الدين",
                        messageEn = "Target currency does not match debt currency"
                    )
                }
                if (target.status == DebtStatus.CANCELED || target.remainingAmountMinor <= 0L) {
                    return@withTransaction PaymentSubmissionResult.ValidationError(
                        messageAr = "هذا الدين مسدد بالكامل أو ملغى",
                        messageEn = "This debt is already settled or canceled"
                    )
                }
                listOf(target)
            } else {
                debtDao.getOpenDebtsFifo(customerId, targetCurrency)
            }

            val totalRemaining = candidateDebts.sumOf { it.remainingAmountMinor }
            if (convertedMinor > totalRemaining && !allowOverpaymentAsCredit) {
                return@withTransaction PaymentSubmissionResult.OverpaymentNeedsConfirmation(
                    openRemainingMinor = totalRemaining,
                    excessCreditMinor = convertedMinor - totalRemaining,
                    targetCurrency = targetCurrency
                )
            }

            val paymentId = UUID.randomUUID().toString()
            val year = SimpleDateFormat("yyyy", Locale.US).format(Date(paymentDate))
            val seq = paymentDao.countAll() + 1
            val receiptNumber = "REC-$year-${seq.toString().padStart(4, '0')}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"

            var remainingToAllocate = convertedMinor
            val allocations = mutableListOf<PaymentAllocationEntity>()
            var totalAllocated = 0L

            for (debt in candidateDebts) {
                if (remainingToAllocate <= 0L) break
                val alloc = minOf(remainingToAllocate, debt.remainingAmountMinor)
                if (alloc > 0L) {
                    val newPaid = debt.paidAmountMinor + alloc
                    val newStatus = if (newPaid >= debt.totalAmountMinor) DebtStatus.PAID else DebtStatus.PARTIAL
                    debtDao.update(debt.copy(paidAmountMinor = newPaid, status = newStatus, updatedAt = now))
                    allocations += PaymentAllocationEntity(
                        id = UUID.randomUUID().toString(),
                        paymentId = paymentId,
                        debtId = debt.id,
                        allocatedMinor = alloc
                    )
                    totalAllocated += alloc
                    remainingToAllocate -= alloc
                }
            }

            val overpaymentCredit = (convertedMinor - totalAllocated).coerceAtLeast(0L)

            val payment = PaymentEntity(
                id = paymentId,
                receiptNumber = receiptNumber,
                customerId = customerId,
                debtId = debtId,
                paymentAmountMinor = paymentAmountMinor,
                paymentCurrency = paymentCurrency,
                targetCurrency = targetCurrency,
                exchangeRateUsed = exchangeRate.stripTrailingZeros().toPlainString(),
                convertedAmountMinor = convertedMinor,
                appliedToDebtsMinor = totalAllocated,
                overpaymentCreditMinor = overpaymentCredit,
                paymentMethod = paymentMethod,
                paymentDate = paymentDate,
                notes = notes.trim(),
                status = PaymentStatus.COMPLETED,
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now
            )

            paymentDao.insert(payment)
            if (allocations.isNotEmpty()) {
                paymentDao.insertAllocations(allocations)
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = paymentId,
                    action = "CREATE",
                    summary = "$receiptNumber: ${MoneyUtils.format(paymentAmountMinor, paymentCurrency, false)} -> ${MoneyUtils.format(convertedMinor, targetCurrency, false)} @ ${payment.exchangeRateUsed}",
                    timestamp = now
                )
            )

            PaymentSubmissionResult.Success(payment)
        }
    }

    /**
     * Reverses a payment safely without deleting financial history and recalculates all affected debt balances.
     */
    suspend fun reversePayment(paymentId: String, reason: String = ""): Boolean {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val payment = paymentDao.getById(paymentId) ?: return@withTransaction false
            if (payment.status == PaymentStatus.REVERSED) return@withTransaction false

            val allocations = paymentDao.getAllocationsForPayment(paymentId)
            paymentDao.update(
                payment.copy(
                    status = PaymentStatus.REVERSED,
                    notes = if (reason.isBlank()) payment.notes else "${payment.notes} [عكس/Reversal: $reason]".trim(),
                    updatedAt = now
                )
            )

            for (alloc in allocations) {
                val debt = debtDao.getById(alloc.debtId) ?: continue
                val recalculatedPaid = paymentDao.sumCompletedAllocatedForDebt(debt.id)
                val newStatus = when {
                    debt.status == DebtStatus.CANCELED -> DebtStatus.CANCELED
                    recalculatedPaid >= debt.totalAmountMinor -> DebtStatus.PAID
                    recalculatedPaid > 0L -> DebtStatus.PARTIAL
                    else -> DebtStatus.OPEN
                }
                debtDao.update(debt.copy(paidAmountMinor = recalculatedPaid, status = newStatus, updatedAt = now))
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = paymentId,
                    action = "REVERSE",
                    summary = "${payment.receiptNumber} reversed (${MoneyUtils.format(payment.convertedAmountMinor, payment.targetCurrency, false)})",
                    timestamp = now
                )
            )
            true
        }
    }

    /**
     * Edits an existing completed payment safely inside an atomic transaction, recalculating balances and logging audit trail.
     */
    suspend fun editPayment(
        paymentId: String,
        newAmountMinor: Long,
        newPaymentCurrency: CurrencyCode,
        newExchangeRate: BigDecimal,
        newMethod: PaymentMethod,
        newDate: Long,
        newNotes: String,
        allowOverpaymentAsCredit: Boolean = true
    ): PaymentSubmissionResult {
        if (newAmountMinor <= 0L || newExchangeRate <= BigDecimal.ZERO) {
            return PaymentSubmissionResult.ValidationError("قيمة غير صالحة", "Invalid amount or rate")
        }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = paymentDao.getById(paymentId)
                ?: return@withTransaction PaymentSubmissionResult.ValidationError("الدفعة غير موجودة", "Payment not found")
            if (existing.status == PaymentStatus.REVERSED) {
                return@withTransaction PaymentSubmissionResult.ValidationError("لا يمكن تعديل دفعة معكوسة", "Cannot edit a reversed payment")
            }

            // 1. Temporarily detach old allocations and recalculate affected debts
            val oldAllocations = paymentDao.getAllocationsForPayment(paymentId)
            val affectedDebtIds = oldAllocations.map { it.debtId }.distinct()
            paymentDao.deleteAllocationsByPayment(paymentId)
            for (dId in affectedDebtIds) {
                val debt = debtDao.getById(dId) ?: continue
                val paid = paymentDao.sumCompletedAllocatedForDebt(dId)
                val status = when {
                    debt.status == DebtStatus.CANCELED -> DebtStatus.CANCELED
                    paid >= debt.totalAmountMinor -> DebtStatus.PAID
                    paid > 0L -> DebtStatus.PARTIAL
                    else -> DebtStatus.OPEN
                }
                debtDao.update(debt.copy(paidAmountMinor = paid, status = status, updatedAt = now))
            }

            // 2. Recompute converted amount & re-allocate
            val convertedMinor = MoneyUtils.convertMinor(
                amountMinor = newAmountMinor,
                from = newPaymentCurrency,
                to = existing.targetCurrency,
                usdToIlsRate = newExchangeRate
            )

            val candidates = if (existing.debtId != null) {
                listOfNotNull(debtDao.getById(existing.debtId)).filter { it.status != DebtStatus.CANCELED }
            } else {
                debtDao.getOpenDebtsFifo(existing.customerId, existing.targetCurrency)
            }

            val totalRemaining = candidates.sumOf { it.remainingAmountMinor }
            if (convertedMinor > totalRemaining && !allowOverpaymentAsCredit) {
                throw IllegalStateException("Overpayment requires explicit customer credit confirmation")
            }

            var remainingToAllocate = convertedMinor
            val newAllocations = mutableListOf<PaymentAllocationEntity>()
            var totalAllocated = 0L

            for (debt in candidates) {
                if (remainingToAllocate <= 0L) break
                val alloc = minOf(remainingToAllocate, debt.remainingAmountMinor)
                if (alloc > 0L) {
                    val newPaid = debt.paidAmountMinor + alloc
                    val newStatus = if (newPaid >= debt.totalAmountMinor) DebtStatus.PAID else DebtStatus.PARTIAL
                    debtDao.update(debt.copy(paidAmountMinor = newPaid, status = newStatus, updatedAt = now))
                    newAllocations += PaymentAllocationEntity(
                        id = UUID.randomUUID().toString(),
                        paymentId = existing.id,
                        debtId = debt.id,
                        allocatedMinor = alloc
                    )
                    totalAllocated += alloc
                    remainingToAllocate -= alloc
                }
            }

            val updated = existing.copy(
                paymentAmountMinor = newAmountMinor,
                paymentCurrency = newPaymentCurrency,
                exchangeRateUsed = newExchangeRate.stripTrailingZeros().toPlainString(),
                convertedAmountMinor = convertedMinor,
                appliedToDebtsMinor = totalAllocated,
                overpaymentCreditMinor = (convertedMinor - totalAllocated).coerceAtLeast(0L),
                paymentMethod = newMethod,
                paymentDate = newDate,
                notes = newNotes.trim(),
                updatedAt = now
            )
            paymentDao.update(updated)
            if (newAllocations.isNotEmpty()) {
                paymentDao.insertAllocations(newAllocations)
            }

            auditLogDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = existing.id,
                    action = "UPDATE",
                    summary = "${existing.receiptNumber} edited: ${MoneyUtils.format(existing.convertedAmountMinor, existing.targetCurrency, false)} -> ${MoneyUtils.format(convertedMinor, existing.targetCurrency, false)}",
                    timestamp = now
                )
            )
            PaymentSubmissionResult.Success(updated)
        }
    }

    suspend fun saveReminder(reminder: ReminderEntity) {
        reminderDao.upsert(reminder)
    }

    suspend fun updateReminderStatus(id: String, status: ReminderStatus) {
        reminderDao.updateStatus(id, status)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.delete(reminder)
    }
}
