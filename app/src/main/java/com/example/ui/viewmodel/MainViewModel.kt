package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DebtBookApp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.backup.DatabaseBackupSnapshot
import com.example.data.export.PdfAndCsvExporter
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReminderChannel
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.preferences.MerchantSettings
import com.example.data.preferences.ThemePreference
import com.example.domain.model.BackupConflictInfo
import com.example.domain.model.CustomerFinancialSummary
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DraftLineItem
import com.example.domain.model.PaymentSubmissionResult
import com.example.domain.model.RestoreMode
import com.example.notifications.ReminderNotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.util.UUID

sealed class UiFeedback {
    data class Message(val ar: String, val en: String, val isError: Boolean = false) : UiFeedback()
}

data class PendingRestoreState(
    val snapshot: DatabaseBackupSnapshot,
    val conflict: BackupConflictInfo
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DebtBookApp
    private val container = app.container
    private val repo = container.repository
    private val settingsStore = container.settingsStore
    private val backupManager = container.backupManager
    private val exchangeRateService = container.exchangeRateService

    val settings: StateFlow<MerchantSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MerchantSettings())

    val dashboardSummary: StateFlow<DashboardSummary> = repo.dashboardSummaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val customerSummaries: StateFlow<List<CustomerFinancialSummary>> = repo.customerSummariesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repo.customersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repo.productsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debts: StateFlow<List<DebtEntity>> = repo.debtsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLineItems: StateFlow<List<DebtLineItemEntity>> = repo.allLineItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = repo.paymentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repo.remindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repo.auditLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _pendingRestore = MutableStateFlow<PendingRestoreState?>(null)
    val pendingRestore: StateFlow<PendingRestoreState?> = _pendingRestore.asStateFlow()

    private val _feedback = MutableSharedFlow<UiFeedback>(extraBufferCapacity = 8)
    val feedback: SharedFlow<UiFeedback> = _feedback.asSharedFlow()

    init {
        viewModelScope.launch {
            val initialSettings = settingsStore.settingsFlow.first()
            _isUnlocked.value = !initialSettings.appLockEnabled
        }
    }

    fun emitMessage(ar: String, en: String, isError: Boolean = false) {
        _feedback.tryEmit(UiFeedback.Message(ar, en, isError))
    }

    fun tryUnlockWithPin(pin: String): Boolean {
        val current = settings.value
        val ok = settingsStore.verifyPin(pin, current)
        if (ok) {
            _isUnlocked.value = true
        } else {
            emitMessage("رمز PIN غير صحيح", "Incorrect PIN", isError = true)
        }
        return ok
    }

    fun markUnlockedBiometric() {
        _isUnlocked.value = true
    }

    fun lockAppNow() {
        if (settings.value.appLockEnabled) {
            _isUnlocked.value = false
        }
    }

    // --- Customers ---
    fun saveCustomer(
        id: String? = null,
        fullName: String,
        phone: String,
        address: String,
        photoUri: String?,
        notes: String,
        onDone: (CustomerEntity) -> Unit = {}
    ) {
        if (fullName.isBlank()) {
            emitMessage("يرجى إدخال اسم الزبون", "Please enter customer name", isError = true)
            return
        }
        viewModelScope.launch {
            runCatching {
                repo.saveCustomer(id, fullName, phone, address, photoUri, notes)
            }.onSuccess { saved ->
                emitMessage(
                    if (id == null) "تمت إضافة الزبون بنجاح" else "تم تحديث بيانات الزبون",
                    if (id == null) "Customer added successfully" else "Customer updated"
                )
                onDone(saved)
            }.onFailure {
                emitMessage("تعذر حفظ الزبون: ${it.localizedMessage}", "Failed to save customer", isError = true)
            }
        }
    }

    fun setCustomerArchived(customerId: String, archived: Boolean) {
        viewModelScope.launch {
            repo.setCustomerArchived(customerId, archived)
            emitMessage(
                if (archived) "تمت أرشفة الزبون" else "تمت استعادة الزبون من الأرشيف",
                if (archived) "Customer archived" else "Customer restored"
            )
        }
    }

    fun deleteCustomer(customerId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val deleted = repo.deleteCustomerIfNoLedgerHistory(customerId)
            if (deleted) {
                emitMessage("تم حذف الزبون", "Customer deleted")
                onDeleted()
            } else {
                emitMessage(
                    "لا يمكن حذف زبون لديه ديون أو دفعات مسجلة. يمكنك أرشفته بدلاً من ذلك.",
                    "Cannot delete customer with ledger history. Archive instead.",
                    isError = true
                )
            }
        }
    }

    // --- Products ---
    fun saveProduct(product: ProductEntity, onDone: () -> Unit = {}) {
        if (product.name.isBlank()) {
            emitMessage("يرجى إدخال اسم المنتج", "Please enter product name", isError = true)
            return
        }
        viewModelScope.launch {
            repo.saveProduct(product)
            emitMessage("تم حفظ المنتج في الكتالوج", "Product saved", false)
            onDone()
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repo.deleteProduct(product)
            emitMessage("تم حذف المنتج", "Product deleted")
        }
    }

    fun importProductsCsv(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            PdfAndCsvExporter.importProductsFromCsvUri(app, uri)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        emitMessage("الملف لا يحتوي على منتجات صالحة", "No valid products found in CSV", isError = true)
                    } else {
                        repo.importProducts(list)
                        emitMessage("تم استيراد ${list.size} منتج بنجاح", "Imported ${list.size} products")
                    }
                }
                .onFailure {
                    emitMessage("فشل استيراد ملف CSV", "Failed to import CSV", isError = true)
                }
            _isBusy.value = false
        }
    }

    fun exportProductsCsv(uri: Uri) {
        viewModelScope.launch {
            val list = products.value
            val headers = listOf("Name", "Code", "PurchasePrice", "SellingPrice", "Currency", "Qty", "Category")
            val rows = list.map { p ->
                listOf(
                    p.name,
                    p.codeOrBarcode,
                    MoneyUtils.formatEditable(p.purchasePriceMinor),
                    MoneyUtils.formatEditable(p.sellingPriceMinor),
                    p.currency.code,
                    p.availableQty.toString(),
                    p.category
                )
            }
            PdfAndCsvExporter.exportCsvToUri(app, uri, headers, rows)
                .onSuccess { count ->
                    emitMessage("تم تصدير $count منتج إلى CSV", "Exported $count products")
                }
                .onFailure {
                    emitMessage("فشل تصدير الكتالوج", "Failed to export catalog", isError = true)
                }
        }
    }

    // --- Debts ---
    fun createDebt(
        customerId: String,
        currency: CurrencyCode,
        manualAmountMinor: Long?,
        lineItems: List<DraftLineItem>,
        discountMinor: Long,
        taxMinor: Long,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?,
        onDone: (DebtEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                repo.createDebt(
                    customerId = customerId,
                    currency = currency,
                    manualAmountMinor = manualAmountMinor,
                    lineItems = lineItems,
                    discountMinor = discountMinor,
                    taxMinor = taxMinor,
                    dueDate = dueDate,
                    notes = notes,
                    receiptImageUri = receiptImageUri
                )
            }.onSuccess { debt ->
                if (dueDate != null && settings.value.notificationsEnabled) {
                    val cName = customers.value.firstOrNull { it.id == customerId }?.fullName ?: ""
                    ReminderNotificationHelper.scheduleReminder(
                        context = app,
                        reminderId = debt.id,
                        triggerAtMillis = dueDate,
                        title = "موعد استحقاق دين: $cName",
                        message = "فاتورة ${debt.invoiceNumber} بمبلغ ${MoneyUtils.format(debt.totalAmountMinor, debt.currency, true)}"
                    )
                }
                emitMessage("تم تسجيل الدين بنجاح", "Debt recorded successfully")
                onDone(debt)
            }.onFailure { err ->
                emitMessage(
                    "تعذر تسجيل الدين: ${err.message ?: "تحقق من الكمية والمبلغ"}",
                    "Failed to record debt: ${err.message ?: "Check amount/stock"}",
                    isError = true
                )
            }
        }
    }

    fun updateDebtDetails(
        debtId: String,
        newManualTotalMinor: Long?,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repo.updateDebtDetails(debtId, newManualTotalMinor, dueDate, notes, receiptImageUri)
            if (ok) {
                emitMessage("تم تحديث بيانات الدين", "Debt updated")
                onDone()
            } else {
                emitMessage("تعذر تحديث الدين", "Could not update debt", isError = true)
            }
        }
    }

    fun cancelDebt(debtId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repo.cancelDebt(debtId) }
                .onSuccess { ok ->
                    if (ok) {
                        emitMessage("تم إلغاء الدين واستعادة المخزون", "Debt canceled and stock restored")
                        onDone()
                    }
                }
                .onFailure {
                    emitMessage(
                        "لا يمكن إلغاء دين عليه دفعات نشطة. قم بعكس الدفعات أولاً.",
                        "Cannot cancel a debt with active payments. Reverse payments first.",
                        isError = true
                    )
                }
        }
    }

    // --- Payments ---
    fun recordPayment(
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
        allowOverpaymentAsCredit: Boolean,
        onResult: (PaymentSubmissionResult) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.recordPayment(
                customerId = customerId,
                debtId = debtId,
                paymentAmountMinor = paymentAmountMinor,
                paymentCurrency = paymentCurrency,
                targetCurrency = targetCurrency,
                exchangeRate = exchangeRate,
                paymentMethod = paymentMethod,
                paymentDate = paymentDate,
                notes = notes,
                idempotencyKey = idempotencyKey,
                allowOverpaymentAsCredit = allowOverpaymentAsCredit
            )
            when (res) {
                is PaymentSubmissionResult.Success ->
                    emitMessage("تم تسجيل الدفعة وتحديث الرصيد", "Payment recorded and balance updated")
                PaymentSubmissionResult.DuplicatePrevented ->
                    emitMessage("تم منع تكرار الدفعة بنجاح", "Duplicate payment prevented", isError = true)
                is PaymentSubmissionResult.ValidationError ->
                    emitMessage(res.messageAr, res.messageEn, isError = true)
                is PaymentSubmissionResult.OverpaymentNeedsConfirmation -> Unit
            }
            onResult(res)
        }
    }

    fun reversePayment(paymentId: String, reason: String = "", onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = repo.reversePayment(paymentId, reason)
            if (ok) {
                emitMessage("تم عكس الدفعة وإعادة احتساب الأرصدة بأمان", "Payment reversed & balances recalculated")
                onDone()
            } else {
                emitMessage("الدفعة معكوسة مسبقاً أو غير موجودة", "Payment already reversed or missing", isError = true)
            }
        }
    }

    fun editPayment(
        paymentId: String,
        newAmountMinor: Long,
        newPaymentCurrency: CurrencyCode,
        newExchangeRate: BigDecimal,
        newMethod: PaymentMethod,
        newDate: Long,
        newNotes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repo.editPayment(
                paymentId, newAmountMinor, newPaymentCurrency, newExchangeRate, newMethod, newDate, newNotes
            )
            if (res is PaymentSubmissionResult.Success) {
                emitMessage("تم تعديل الدفعة وتحديث سجل التدقيق", "Payment updated with audit trail")
                onDone()
            } else if (res is PaymentSubmissionResult.ValidationError) {
                emitMessage(res.messageAr, res.messageEn, isError = true)
            }
        }
    }

    // --- Reminders ---
    fun createReminder(
        customer: CustomerEntity,
        debtId: String?,
        title: String,
        message: String,
        dueAt: Long,
        channel: ReminderChannel
    ) {
        viewModelScope.launch {
            val entity = ReminderEntity(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                debtId = debtId,
                title = title.trim(),
                message = message.trim(),
                dueAt = dueAt,
                status = ReminderStatus.SCHEDULED,
                channel = channel,
                createdAt = System.currentTimeMillis()
            )
            repo.saveReminder(entity)
            if (channel == ReminderChannel.LOCAL_NOTIFICATION && settings.value.notificationsEnabled) {
                ReminderNotificationHelper.scheduleReminder(
                    app, entity.id, dueAt, entity.title, entity.message
                )
            }
            emitMessage("تم حفظ التذكير", "Reminder saved")
        }
    }

    fun markReminderStatus(id: String, status: ReminderStatus) {
        viewModelScope.launch {
            repo.updateReminderStatus(id, status)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repo.deleteReminder(reminder)
        }
    }

    // --- PDF & Reports Export ---
    fun exportInvoicePdf(debt: DebtEntity, customer: CustomerEntity, shareWhatsApp: Boolean, print: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val items = allLineItems.value.filter { it.debtId == debt.id }
            val s = settings.value
            runCatching {
                PdfAndCsvExporter.generateInvoicePdf(app, s, customer, debt, items, s.isArabic)
            }.onSuccess { file ->
                handleGeneratedPdf(file, "Invoice ${debt.invoiceNumber}", shareWhatsApp, print)
            }.onFailure {
                emitMessage("فشل إنشاء ملف PDF", "Failed to generate PDF", isError = true)
            }
            _isBusy.value = false
        }
    }

    fun exportReceiptPdf(payment: PaymentEntity, customer: CustomerEntity, shareWhatsApp: Boolean, print: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val s = settings.value
            runCatching {
                PdfAndCsvExporter.generatePaymentReceiptPdf(app, s, customer, payment, s.isArabic)
            }.onSuccess { file ->
                handleGeneratedPdf(file, "Receipt ${payment.receiptNumber}", shareWhatsApp, print)
            }.onFailure {
                emitMessage("فشل إنشاء إيصال PDF", "Failed to generate receipt PDF", isError = true)
            }
            _isBusy.value = false
        }
    }

    fun exportCustomerStatementPdf(summary: CustomerFinancialSummary, shareWhatsApp: Boolean, print: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val s = settings.value
            val cDebts = debts.value.filter { it.customerId == summary.customer.id }
            val cPays = payments.value.filter { it.customerId == summary.customer.id }
            runCatching {
                PdfAndCsvExporter.generateCustomerStatementPdf(app, s, summary, cDebts, cPays, s.isArabic)
            }.onSuccess { file ->
                handleGeneratedPdf(file, "Statement ${summary.customer.fullName}", shareWhatsApp, print)
            }.onFailure {
                emitMessage("فشل إنشاء كشف الحساب", "Failed to generate statement", isError = true)
            }
            _isBusy.value = false
        }
    }

    fun exportReportPdf(title: String, rows: List<Pair<String, String>>) {
        viewModelScope.launch {
            _isBusy.value = true
            val s = settings.value
            runCatching {
                PdfAndCsvExporter.generateFinancialReportPdf(app, s, title, rows, s.isArabic)
            }.onSuccess { file ->
                PdfAndCsvExporter.shareFile(app, file, "application/pdf", title, preferWhatsApp = false)
            }.onFailure {
                emitMessage("فشل تصدير التقرير", "Failed to export report", isError = true)
            }
            _isBusy.value = false
        }
    }

    fun exportReportCsv(uri: Uri, headers: List<String>, rows: List<List<String>>) {
        viewModelScope.launch {
            PdfAndCsvExporter.exportCsvToUri(app, uri, headers, rows)
                .onSuccess {
                    emitMessage("تم تصدير التقرير بصيغة Excel CSV", "Report exported to CSV")
                }
                .onFailure {
                    emitMessage("فشل تصدير CSV", "CSV export failed", isError = true)
                }
        }
    }

    private fun handleGeneratedPdf(file: File, jobName: String, shareWhatsApp: Boolean, print: Boolean) {
        if (print) {
            PdfAndCsvExporter.printPdfFile(app, file, jobName)
        } else {
            PdfAndCsvExporter.shareFile(
                context = app,
                file = file,
                mimeType = "application/pdf",
                chooserTitle = jobName,
                preferWhatsApp = shareWhatsApp
            )
        }
    }

    // --- Backup & Restore ---
    fun exportBackup(uri: Uri, passphrase: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            backupManager.exportToUri(uri, passphrase)
                .onSuccess { ts ->
                    settingsStore.setLastBackupAt(ts)
                    emitMessage(
                        if (passphrase.isNullOrBlank()) "تم تصدير النسخة الاحتياطية بنجاح"
                        else "تم تصدير النسخة الاحتياطية المشفرة (AES-256) بنجاح",
                        if (passphrase.isNullOrBlank()) "Backup exported successfully"
                        else "Encrypted AES-256 backup exported"
                    )
                }
                .onFailure {
                    emitMessage("فشل تصدير النسخة الاحتياطية", "Backup export failed", isError = true)
                }
            _isBusy.value = false
        }
    }

    fun inspectRestoreBackup(uri: Uri, passphrase: String?, onNeedPassphrase: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            backupManager.parseBackupFromUri(uri, passphrase)
                .onSuccess { snapshot ->
                    val conflict = backupManager.inspectConflict(snapshot)
                    _pendingRestore.value = PendingRestoreState(snapshot, conflict)
                }
                .onFailure { err ->
                    if (err.message?.contains("PASSPHRASE_REQUIRED") == true) {
                        onNeedPassphrase()
                    } else {
                        emitMessage(
                            "تعذر قراءة ملف النسخة أو كلمة المرور غير صحيحة",
                            "Failed to decrypt or parse backup file",
                            isError = true
                        )
                    }
                }
            _isBusy.value = false
        }
    }

    fun confirmRestore(mode: RestoreMode) {
        val pending = _pendingRestore.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            backupManager.restoreSnapshot(pending.snapshot, mode)
                .onSuccess {
                    _pendingRestore.value = null
                    emitMessage("تمت استعادة البيانات بنجاح", "Data restored successfully")
                }
                .onFailure {
                    emitMessage("فشلت استعادة البيانات", "Restore failed", isError = true)
                }
            _isBusy.value = false
        }
    }

    fun dismissPendingRestore() {
        _pendingRestore.value = null
    }

    // --- Settings & Exchange Rate ---
    fun updateMerchantProfile(name: String, phone: String, logoUri: String?) {
        viewModelScope.launch {
            settingsStore.updateMerchantProfile(name, phone, logoUri)
            emitMessage("تم حفظ بيانات المتجر", "Merchant profile saved")
        }
    }

    fun setDefaultCurrency(currency: CurrencyCode) {
        viewModelScope.launch { settingsStore.setDefaultCurrency(currency) }
    }

    fun setManualExchangeRate(rateText: String) {
        val parsed = MoneyUtils.parsePositiveRate(rateText)
        if (parsed == null) {
            emitMessage("سعر الصرف غير صالح", "Invalid exchange rate", isError = true)
            return
        }
        viewModelScope.launch {
            settingsStore.setExchangeRate(parsed.stripTrailingZeros().toPlainString())
            emitMessage("تم تحديث سعر الصرف: 1 USD = $parsed ILS", "Exchange rate updated")
        }
    }

    fun refreshExchangeRateOnline() {
        viewModelScope.launch {
            _isBusy.value = true
            exchangeRateService.fetchUsdToIlsRate()
                .onSuccess { rate ->
                    settingsStore.setExchangeRate(rate.stripTrailingZeros().toPlainString())
                    emitMessage("تم تحديث سعر الصرف تلقائياً: 1 USD = $rate ILS", "Live rate updated: 1 USD = $rate ILS")
                }
                .onFailure {
                    emitMessage(
                        "تعذر الاتصال بخدمة أسعار الصرف. يمكنك إدخال السعر يدوياً.",
                        "Could not reach exchange rate API. Enter rate manually.",
                        isError = true
                    )
                }
            _isBusy.value = false
        }
    }

    fun setLanguageArabic(isArabic: Boolean) {
        viewModelScope.launch { settingsStore.setLanguageArabic(isArabic) }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(pref) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoBackupEnabled(enabled) }
    }

    fun enablePinLock(pin: String, biometric: Boolean) {
        if (pin.length < 4) {
            emitMessage("رمز PIN يجب أن يكون 4 أرقام على الأقل", "PIN must be at least 4 digits", isError = true)
            return
        }
        viewModelScope.launch {
            settingsStore.enablePinLock(pin, biometric)
            emitMessage("تم تفعيل قفل التطبيق", "App lock enabled")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBiometricEnabled(enabled) }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            settingsStore.disableAppLock()
            emitMessage("تم إيقاف قفل التطبيق", "App lock disabled")
        }
    }
}
