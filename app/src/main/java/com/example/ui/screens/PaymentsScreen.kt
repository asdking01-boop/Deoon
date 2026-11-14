package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.domain.model.PaymentSubmissionResult
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import java.math.BigDecimal
import java.util.UUID

@Composable
fun PaymentsScreen(
    payments: List<PaymentEntity>,
    customers: List<CustomerEntity>,
    debts: List<DebtEntity>,
    defaultCurrency: CurrencyCode,
    defaultUsdToIlsRate: String,
    isArabic: Boolean,
    onOpenPayment: (String) -> Unit,
    onSubmitPayment: (
        customerId: String,
        debtId: String?,
        paymentAmountMinor: Long,
        paymentCurrency: CurrencyCode,
        targetCurrency: CurrencyCode,
        exchangeRate: BigDecimal,
        method: PaymentMethod,
        paymentDate: Long,
        notes: String,
        idempotencyKey: String,
        allowOverpaymentAsCredit: Boolean,
        onResult: (PaymentSubmissionResult) -> Unit
    ) -> Unit,
    preselectedCustomerId: String? = null,
    preselectedDebtId: String? = null,
    showAddDialogInitially: Boolean = false,
    onAddDialogConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<PaymentStatus?>(null) }
    var methodFilter by remember { mutableStateOf<PaymentMethod?>(null) }
    var showAddDialog by remember(showAddDialogInitially) {
        mutableStateOf(showAddDialogInitially).also {
            if (showAddDialogInitially) onAddDialogConsumed()
        }
    }

    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val filtered = remember(payments, search, statusFilter, methodFilter, customerMap) {
        payments.filter { p ->
            val cName = customerMap[p.customerId]?.fullName ?: ""
            val matchQ = search.isBlank() ||
                p.receiptNumber.contains(search, ignoreCase = true) ||
                cName.contains(search, ignoreCase = true)
            val matchS = statusFilter == null || p.status == statusFilter
            val matchM = methodFilter == null || p.paymentMethod == methodFilter
            matchQ && matchS && matchM
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr(isArabic, "سجل الدفعات (${filtered.size})", "Payments (${filtered.size})"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.emerald),
                    modifier = Modifier.testTag("add_payment_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(isArabic, "دفعة جديدة", "Add Payment"))
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr(isArabic, "ابحث برقم الإيصال أو اسم الزبون...", "Search receipt # or customer...")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text(tr(isArabic, "الكل", "All")) })
                FilterChip(selected = statusFilter == PaymentStatus.COMPLETED, onClick = { statusFilter = PaymentStatus.COMPLETED }, label = { Text(tr(isArabic, "مكتملة", "Completed")) })
                FilterChip(selected = statusFilter == PaymentStatus.REVERSED, onClick = { statusFilter = PaymentStatus.REVERSED }, label = { Text(tr(isArabic, "معكوسة", "Reversed")) })
                FilterChip(selected = methodFilter == PaymentMethod.CASH, onClick = { methodFilter = if (methodFilter == PaymentMethod.CASH) null else PaymentMethod.CASH }, label = { Text(PaymentMethod.CASH.localized(isArabic)) })
                FilterChip(selected = methodFilter == PaymentMethod.BANK_TRANSFER, onClick = { methodFilter = if (methodFilter == PaymentMethod.BANK_TRANSFER) null else PaymentMethod.BANK_TRANSFER }, label = { Text(PaymentMethod.BANK_TRANSFER.localized(isArabic)) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Rounded.Payments,
                        title = tr(isArabic, "لا توجد دفعات مسجلة", "No Payments Recorded"),
                        subtitle = tr(
                            isArabic,
                            "سجّل دفعات الزبائن نقداً أو تحويل بنكي بأي عملة مع حفظ سعر الصرف التاريخي.",
                            "Record customer payments in ILS or USD with documented historical exchange rates."
                        ),
                        actionLabel = tr(isArabic, "تسجيل دفعة", "Add Payment"),
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { pay ->
                    val custName = customerMap[pay.customerId]?.fullName ?: "—"
                    val reversed = pay.status == PaymentStatus.REVERSED
                    GlassFinanceCard(onClick = { onOpenPayment(pay.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(custName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${pay.receiptNumber} • ${pay.paymentMethod.localized(isArabic)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                                Text(
                                    formatDateTime(pay.paymentDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    MoneyUtils.format(pay.paymentAmountMinor, pay.paymentCurrency, isArabic),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (reversed) ext.mutedText else ext.emerald,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pay.paymentCurrency != pay.targetCurrency) {
                                    Text(
                                        "= ${MoneyUtils.format(pay.convertedAmountMinor, pay.targetCurrency, isArabic)} @ ${pay.exchangeRateUsed}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.gold
                                    )
                                }
                                if (reversed) {
                                    StatusBadge(
                                        text = pay.status.localized(isArabic),
                                        containerColor = ext.crimsonContainer,
                                        contentColor = ext.crimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPaymentDialog(
            customers = customers.filter { !it.isArchived },
            debts = debts.filter { it.status == DebtStatus.OPEN || it.status == DebtStatus.PARTIAL },
            defaultCurrency = defaultCurrency,
            defaultUsdToIlsRate = defaultUsdToIlsRate,
            preselectedCustomerId = preselectedCustomerId,
            preselectedDebtId = preselectedDebtId,
            isArabic = isArabic,
            onDismiss = { showAddDialog = false },
            onSubmit = onSubmitPayment,
            onCompleted = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(
    customers: List<CustomerEntity>,
    debts: List<DebtEntity>,
    defaultCurrency: CurrencyCode,
    defaultUsdToIlsRate: String,
    preselectedCustomerId: String?,
    preselectedDebtId: String?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        customerId: String,
        debtId: String?,
        paymentAmountMinor: Long,
        paymentCurrency: CurrencyCode,
        targetCurrency: CurrencyCode,
        exchangeRate: BigDecimal,
        method: PaymentMethod,
        paymentDate: Long,
        notes: String,
        idempotencyKey: String,
        allowOverpaymentAsCredit: Boolean,
        onResult: (PaymentSubmissionResult) -> Unit
    ) -> Unit,
    onCompleted: () -> Unit
) {
    val ext = LocalDebtBookColors.current
    val initialDebt = debts.firstOrNull { it.id == preselectedDebtId }
    var selectedCustomer by remember {
        mutableStateOf(
            customers.firstOrNull { it.id == (initialDebt?.customerId ?: preselectedCustomerId) }
                ?: customers.firstOrNull()
        )
    }
    var customerExpanded by remember { mutableStateOf(false) }

    val customerOpenDebts = remember(selectedCustomer, debts) {
        debts.filter { it.customerId == selectedCustomer?.id }
    }
    var selectedDebt by remember(selectedCustomer) {
        mutableStateOf(initialDebt?.takeIf { it.customerId == selectedCustomer?.id })
    }
    var debtExpanded by remember { mutableStateOf(false) }

    var targetCurrency by remember(selectedDebt) {
        mutableStateOf(selectedDebt?.currency ?: defaultCurrency)
    }
    var paymentCurrency by remember { mutableStateOf(selectedDebt?.currency ?: defaultCurrency) }
    var amountText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf(defaultUsdToIlsRate) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var paymentDate by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val idempotencyKey = remember { UUID.randomUUID().toString() }
    var pendingOverpayment by remember { mutableStateOf<PaymentSubmissionResult.OverpaymentNeedsConfirmation?>(null) }

    val parsedAmountMinor = MoneyUtils.parseToMinorUnits(amountText)
    val parsedRate = MoneyUtils.parsePositiveRate(rateText)
    val convertedPreviewMinor = if (parsedAmountMinor != null && parsedRate != null) {
        MoneyUtils.convertMinor(parsedAmountMinor, paymentCurrency, targetCurrency, parsedRate)
    } else null

    fun triggerSubmit(allowOverpayment: Boolean) {
        val c = selectedCustomer ?: return
        val amt = parsedAmountMinor ?: return
        val rate = parsedRate ?: return
        isSubmitting = true
        onSubmit(
            c.id,
            selectedDebt?.id,
            amt,
            paymentCurrency,
            targetCurrency,
            rate,
            method,
            paymentDate ?: System.currentTimeMillis(),
            notes,
            idempotencyKey,
            allowOverpayment
        ) { result ->
            isSubmitting = false
            when (result) {
                is PaymentSubmissionResult.Success -> onCompleted()
                is PaymentSubmissionResult.OverpaymentNeedsConfirmation -> pendingOverpayment = result
                else -> Unit
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(isArabic, "تسجيل دفعة جديدة", "Record Payment")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customers.isEmpty()) {
                    Text(tr(isArabic, "لا يوجد زبائن مسجلون بعد.", "No customers available yet."), color = ext.crimson)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = customerExpanded,
                        onExpandedChange = { customerExpanded = !customerExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.fullName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(isArabic, "الزبون *", "Customer *")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                            customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.fullName) },
                                    onClick = {
                                        selectedCustomer = c
                                        customerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = debtExpanded,
                        onExpandedChange = { debtExpanded = !debtExpanded }
                    ) {
                        val debtLabel = selectedDebt?.let {
                            "${it.invoiceNumber} (${MoneyUtils.format(it.remainingAmountMinor, it.currency, isArabic)})"
                        } ?: tr(isArabic, "توزيع تلقائي على رصيد الزبون (الأقدم أولاً)", "Apply to Customer Balance (FIFO)")

                        OutlinedTextField(
                            value = debtLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(isArabic, "تخصيص الدفعة", "Apply To")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debtExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = debtExpanded, onDismissRequest = { debtExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(tr(isArabic, "رصيد الزبون العام (FIFO)", "General Customer Balance (FIFO)")) },
                                onClick = {
                                    selectedDebt = null
                                    debtExpanded = false
                                }
                            )
                            customerOpenDebts.forEach { d ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${d.invoiceNumber} — ${MoneyUtils.format(d.remainingAmountMinor, d.currency, isArabic)}")
                                    },
                                    onClick = {
                                        selectedDebt = d
                                        targetCurrency = d.currency
                                        debtExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedDebt == null) {
                        Text(tr(isArabic, "عملة حساب الدين المستهدف:", "Target Debt Account Currency:"), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = targetCurrency == CurrencyCode.ILS, onClick = { targetCurrency = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                            FilterChip(selected = targetCurrency == CurrencyCode.USD, onClick = { targetCurrency = CurrencyCode.USD }, label = { Text("USD $") })
                        }
                    }

                    Text(tr(isArabic, "عملة الدفع المستلمة:", "Payment Currency Received:"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = paymentCurrency == CurrencyCode.ILS, onClick = { paymentCurrency = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                        FilterChip(selected = paymentCurrency == CurrencyCode.USD, onClick = { paymentCurrency = CurrencyCode.USD }, label = { Text("USD $") })
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(tr(isArabic, "المبلغ المدفوع (${paymentCurrency.symbol}) *", "Amount (${paymentCurrency.code}) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )

                    if (paymentCurrency != targetCurrency) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it },
                            label = { Text(tr(isArabic, "سعر الصرف (1 USD = ? ILS)", "Exchange Rate (1 USD = ? ILS)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_rate_input")
                        )
                        if (convertedPreviewMinor != null) {
                            GlassFinanceCard(borderColor = ext.gold) {
                                Text(
                                    "${tr(isArabic, "القيمة المحوّلة:", "Converted:")} ${MoneyUtils.format(convertedPreviewMinor, targetCurrency, isArabic)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ext.gold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaymentMethod.entries.forEach { m ->
                            FilterChip(
                                selected = method == m,
                                onClick = { method = m },
                                label = { Text(m.localized(isArabic)) }
                            )
                        }
                    }

                    DatePickerField(
                        label = tr(isArabic, "تاريخ الدفع", "Payment Date"),
                        selectedMillis = paymentDate,
                        isArabic = isArabic,
                        onDateSelected = { paymentDate = it },
                        allowClear = false
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(isArabic, "ملاحظات", "Notes")) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { triggerSubmit(false) },
                enabled = !isSubmitting && selectedCustomer != null && parsedAmountMinor != null && parsedRate != null,
                colors = ButtonDefaults.buttonColors(containerColor = ext.emerald),
                modifier = Modifier.testTag("save_payment_button")
            ) {
                Text(tr(isArabic, "اعتماد الدفعة", "Submit Payment"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(isArabic, "إلغاء", "Cancel"))
            }
        }
    )

    pendingOverpayment?.let { over ->
        AlertDialog(
            onDismissRequest = { pendingOverpayment = null },
            title = { Text(tr(isArabic, "الدفعة تتجاوز الرصيد المستحق", "Payment Exceeds Open Balance")) },
            text = {
                Text(
                    tr(
                        isArabic,
                        "الرصيد المستحق هو ${MoneyUtils.format(over.openRemainingMinor, over.targetCurrency, true)}. هل تريد تسجيل الفائض (${MoneyUtils.format(over.excessCreditMinor, over.targetCurrency, true)}) كرصيد دائن للزبون؟",
                        "Open balance is ${MoneyUtils.format(over.openRemainingMinor, over.targetCurrency, false)}. Record excess (${MoneyUtils.format(over.excessCreditMinor, over.targetCurrency, false)}) as explicit Customer Credit?"
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingOverpayment = null
                    triggerSubmit(true)
                }) {
                    Text(tr(isArabic, "تأكيد كرصيد دائن", "Confirm Credit"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverpayment = null }) {
                    Text(tr(isArabic, "تعديل المبلغ", "Adjust Amount"))
                }
            }
        )
    }
}
