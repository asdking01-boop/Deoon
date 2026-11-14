package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.PaymentStatus
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetailScreen(
    payment: PaymentEntity?,
    customer: CustomerEntity?,
    isArabic: Boolean,
    onBack: () -> Unit,
    onEditPayment: (amountMinor: Long, currency: CurrencyCode, rate: BigDecimal, method: PaymentMethod, notes: String) -> Unit,
    onReversePayment: (reason: String) -> Unit,
    onExportReceiptPdf: (shareWhatsApp: Boolean, print: Boolean) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var showReverseDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(payment?.receiptNumber ?: tr(isArabic, "تفاصيل الدفعة", "Payment Receipt")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    if (payment != null && payment.status == PaymentStatus.COMPLETED) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = tr(isArabic, "تعديل", "Edit"))
                        }
                        IconButton(onClick = { showReverseDialog = true }) {
                            Icon(Icons.Rounded.Undo, contentDescription = tr(isArabic, "عكس الدفعة", "Reverse"), tint = ext.crimson)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (payment == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(tr(isArabic, "الدفعة غير موجودة", "Payment not found"))
            }
            return@Scaffold
        }

        val isRev = payment.status == PaymentStatus.REVERSED
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassFinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(customer?.fullName ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(payment.receiptNumber, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        }
                        StatusBadge(
                            text = payment.status.localized(isArabic),
                            containerColor = if (isRev) ext.crimsonContainer else ext.emeraldContainer,
                            contentColor = if (isRev) ext.crimson else ext.emerald
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    DetailLine(tr(isArabic, "المبلغ المدفوع:", "Paid Amount:"), MoneyUtils.format(payment.paymentAmountMinor, payment.paymentCurrency, isArabic))
                    DetailLine(tr(isArabic, "عملة الدفع:", "Payment Currency:"), payment.paymentCurrency.label(isArabic))
                    DetailLine(tr(isArabic, "عملة الحساب:", "Account Currency:"), payment.targetCurrency.label(isArabic))
                    DetailLine(tr(isArabic, "سعر الصرف المحفوظ:", "Saved Rate:"), "1 USD = ${payment.exchangeRateUsed} ILS")
                    DetailLine(tr(isArabic, "القيمة المحوّلة:", "Converted Value:"), MoneyUtils.format(payment.convertedAmountMinor, payment.targetCurrency, isArabic))
                    if (payment.overpaymentCreditMinor > 0L) {
                        DetailLine(tr(isArabic, "رصيد دائن:", "Customer Credit:"), MoneyUtils.format(payment.overpaymentCreditMinor, payment.targetCurrency, isArabic))
                    }
                    DetailLine(tr(isArabic, "طريقة الدفع:", "Method:"), payment.paymentMethod.localized(isArabic))
                    DetailLine(tr(isArabic, "تاريخ الدفع:", "Date:"), formatDateTime(payment.paymentDate))
                    if (payment.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${tr(isArabic, "ملاحظات:", "Notes:")} ${payment.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExportReceiptPdf(false, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(onClick = { onExportReceiptPdf(true, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(isArabic, "واتساب", "WhatsApp"))
                    }
                    OutlinedButton(onClick = { onExportReceiptPdf(false, true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(isArabic, "طباعة", "Print"))
                    }
                }
            }
        }
    }

    if (showReverseDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReverseDialog = false },
            title = { Text(tr(isArabic, "عكس الدفعة وحفظ السجل", "Reverse Payment")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            isArabic,
                            "لن يتم حذف الدفعة من السجل المالي. سيتم تعليمها كمعكوسة وإعادة احتساب أرصدة الديون تلقائياً.",
                            "The payment remains in the ledger marked as REVERSED, and debt balances are safely recalculated."
                        )
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(tr(isArabic, "سبب العكس (اختياري)", "Reason (optional)")) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReverseDialog = false
                        onReversePayment(reason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.crimson)
                ) {
                    Text(tr(isArabic, "تأكيد العكس", "Confirm Reversal"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReverseDialog = false }) {
                    Text(tr(isArabic, "إلغاء", "Cancel"))
                }
            }
        )
    }

    if (showEditDialog && payment != null) {
        var amtText by remember { mutableStateOf(MoneyUtils.formatEditable(payment.paymentAmountMinor)) }
        var pCurr by remember { mutableStateOf(payment.paymentCurrency) }
        var rateText by remember { mutableStateOf(payment.exchangeRateUsed) }
        var method by remember { mutableStateOf(payment.paymentMethod) }
        var notes by remember { mutableStateOf(payment.notes) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(tr(isArabic, "تعديل الدفعة", "Edit Payment")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { amtText = it },
                        label = { Text(tr(isArabic, "المبلغ", "Amount")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = pCurr == CurrencyCode.ILS, onClick = { pCurr = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                        FilterChip(selected = pCurr == CurrencyCode.USD, onClick = { pCurr = CurrencyCode.USD }, label = { Text("USD $") })
                    }
                    if (pCurr != payment.targetCurrency) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it },
                            label = { Text(tr(isArabic, "سعر الصرف", "Exchange Rate")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethod.entries.forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.localized(isArabic)) })
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(isArabic, "ملاحظات", "Notes")) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = MoneyUtils.parseToMinorUnits(amtText) ?: return@Button
                    val rate = MoneyUtils.parsePositiveRate(rateText) ?: return@Button
                    showEditDialog = false
                    onEditPayment(amt, pCurr, rate, method, notes)
                }) {
                    Text(tr(isArabic, "حفظ التعديل", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(tr(isArabic, "إلغاء", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalDebtBookColors.current.mutedText)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
