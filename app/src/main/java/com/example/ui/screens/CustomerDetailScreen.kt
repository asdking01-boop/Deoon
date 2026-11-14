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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.export.PdfAndCsvExporter
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentStatus
import com.example.domain.model.CustomerFinancialSummary
import com.example.domain.model.isOverdue
import com.example.ui.components.CurrencyLedgerCard
import com.example.ui.components.CustomerAvatar
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateShort
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    summary: CustomerFinancialSummary?,
    debts: List<DebtEntity>,
    payments: List<PaymentEntity>,
    isArabic: Boolean,
    onBack: () -> Unit,
    onEditCustomer: (name: String, phone: String, address: String, photoUri: String?, notes: String) -> Unit,
    onToggleArchive: (Boolean) -> Unit,
    onDeleteCustomer: () -> Unit,
    onAddDebtForCustomer: () -> Unit,
    onAddPaymentForCustomer: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    onExportStatementPdf: (shareWhatsApp: Boolean) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalDebtBookColors.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.customer?.fullName ?: tr(isArabic, "حساب الزبون", "Customer Account")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    if (summary != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = tr(isArabic, "تعديل", "Edit"))
                        }
                        IconButton(onClick = { onToggleArchive(!summary.customer.isArchived) }) {
                            Icon(
                                if (summary.customer.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                contentDescription = tr(isArabic, "أرشفة", "Archive")
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = tr(isArabic, "حذف", "Delete"), tint = ext.crimson)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(tr(isArabic, "الزبون غير موجود", "Customer not found"))
            }
            return@Scaffold
        }

        val c = summary.customer
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassFinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomerAvatar(name = c.fullName, photoUri = c.photoUri, size = 60.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = ext.mutedText)
                            if (c.address.isNotBlank()) Text(c.address, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                            Text(
                                "${tr(isArabic, "تاريخ التسجيل:", "Created:")} ${formatDateShort(c.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText
                            )
                        }
                    }
                    if (c.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(c.notes, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                    }
                }
            }

            // Separate ILS and USD Account Ledgers
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CurrencyLedgerCard(
                        currency = CurrencyCode.ILS,
                        outstandingMinor = summary.outstandingIlsMinor,
                        collectedMinor = summary.paidIlsMinor,
                        overdueMinor = debts.filter { it.currency == CurrencyCode.ILS && it.isOverdue() }.sumOf { it.remainingAmountMinor },
                        isArabic = isArabic
                    )
                    CurrencyLedgerCard(
                        currency = CurrencyCode.USD,
                        outstandingMinor = summary.outstandingUsdMinor,
                        collectedMinor = summary.paidUsdMinor,
                        overdueMinor = debts.filter { it.currency == CurrencyCode.USD && it.isOverdue() }.sumOf { it.remainingAmountMinor },
                        isArabic = isArabic
                    )
                    if (summary.creditIlsMinor > 0L || summary.creditUsdMinor > 0L) {
                        GlassFinanceCard(borderColor = ext.emerald) {
                            Text(
                                text = tr(isArabic, "رصيد دائن للعميل (دفعات زائدة معتمدة)", "Customer Overpayment Credit"),
                                style = MaterialTheme.typography.titleSmall,
                                color = ext.emerald,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (summary.creditIlsMinor > 0L) {
                                    Text(MoneyUtils.format(summary.creditIlsMinor, CurrencyCode.ILS, isArabic), fontWeight = FontWeight.Bold)
                                }
                                if (summary.creditUsdMinor > 0L) {
                                    Text(MoneyUtils.format(summary.creditUsdMinor, CurrencyCode.USD, isArabic), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddDebtForCustomer,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.crimson)
                    ) {
                        Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(isArabic, "دين جديد", "Add Debt"))
                    }
                    Button(
                        onClick = onAddPaymentForCustomer,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.emerald)
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(isArabic, "تسجيل دفعة", "Add Payment"))
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onExportStatementPdf(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(isArabic, "كشف حساب PDF", "PDF Statement"))
                    }
                    OutlinedButton(
                        onClick = {
                            val reminderMsg = if (isArabic) {
                                "السلام عليكم ${c.fullName}، الرصيد المستحق في حسابكم:\n" +
                                    (if (summary.outstandingIlsMinor > 0) "• ${MoneyUtils.format(summary.outstandingIlsMinor, CurrencyCode.ILS, true)}\n" else "") +
                                    (if (summary.outstandingUsdMinor > 0) "• ${MoneyUtils.format(summary.outstandingUsdMinor, CurrencyCode.USD, true)}\n" else "") +
                                    "شكراً لتعاونكم."
                            } else {
                                "Hello ${c.fullName}, your current outstanding balance:\n" +
                                    (if (summary.outstandingIlsMinor > 0) "• ${MoneyUtils.format(summary.outstandingIlsMinor, CurrencyCode.ILS, false)}\n" else "") +
                                    (if (summary.outstandingUsdMinor > 0) "• ${MoneyUtils.format(summary.outstandingUsdMinor, CurrencyCode.USD, false)}\n" else "")
                            }
                            PdfAndCsvExporter.shareTextViaWhatsApp(context, c.phone, reminderMsg)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(isArabic, "واتساب", "WhatsApp"))
                    }
                }
            }

            // Debts History
            item {
                Text(
                    tr(isArabic, "سجل الديون (${debts.size})", "Debts (${debts.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(debts, key = { it.id }) { debt ->
                GlassFinanceCard(onClick = { onOpenDebt(debt.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debt.invoiceNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(formatDateTime(debt.createdAt), style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                MoneyUtils.format(debt.totalAmountMinor, debt.currency, isArabic),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(
                                text = "${debt.status.localized(isArabic)} • ${MoneyUtils.format(debt.remainingAmountMinor, debt.currency, isArabic)}",
                                containerColor = if (debt.remainingAmountMinor > 0L) ext.crimsonContainer else ext.emeraldContainer,
                                contentColor = if (debt.remainingAmountMinor > 0L) ext.crimson else ext.emerald
                            )
                        }
                    }
                }
            }

            // Payments History
            item {
                Text(
                    tr(isArabic, "سجل الدفعات (${payments.size})", "Payments (${payments.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(payments, key = { it.id }) { pay ->
                val rev = pay.status == PaymentStatus.REVERSED
                GlassFinanceCard(onClick = { onOpenPayment(pay.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pay.receiptNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${pay.paymentMethod.localized(isArabic)} • ${formatDateTime(pay.paymentDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                MoneyUtils.format(pay.paymentAmountMinor, pay.paymentCurrency, isArabic),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (rev) ext.mutedText else ext.emerald,
                                fontWeight = FontWeight.Bold
                            )
                            if (pay.paymentCurrency != pay.targetCurrency) {
                                Text(
                                    "= ${MoneyUtils.format(pay.convertedAmountMinor, pay.targetCurrency, isArabic)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.gold
                                )
                            }
                            if (rev) {
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

    if (showEditDialog && summary != null) {
        CustomerEditorDialog(
            initial = summary.customer,
            isArabic = isArabic,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, address, photo, notes ->
                onEditCustomer(name, phone, address, photo, notes)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr(isArabic, "تأكيد حذف الزبون", "Confirm Customer Deletion")) },
            text = {
                Text(
                    tr(
                        isArabic,
                        "سيتم حذف الزبون نهائياً إذا لم يكن لديه أي ديون أو دفعات. هل أنت متأكد؟",
                        "Customer will be permanently deleted only if they have no financial history. Continue?"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCustomer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.crimson)
                ) {
                    Text(tr(isArabic, "حذف", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(tr(isArabic, "إلغاء", "Cancel"))
                }
            }
        )
    }
}
