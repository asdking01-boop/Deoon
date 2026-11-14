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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.DebtStatus
import com.example.ui.components.DatePickerField
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateShort
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    debt: DebtEntity?,
    customer: CustomerEntity?,
    lineItems: List<DebtLineItemEntity>,
    isArabic: Boolean,
    onBack: () -> Unit,
    onPayDebt: () -> Unit,
    onEditDebt: (newTotalMinor: Long?, dueDate: Long?, notes: String) -> Unit,
    onCancelDebt: () -> Unit,
    onExportPdf: (shareWhatsApp: Boolean, print: Boolean) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(debt?.invoiceNumber ?: tr(isArabic, "تفاصيل الدين", "Debt Details")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    if (debt != null && debt.status != DebtStatus.CANCELED) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = tr(isArabic, "تعديل", "Edit"))
                        }
                        if (debt.paidAmountMinor == 0L) {
                            IconButton(onClick = { showCancelConfirm = true }) {
                                Icon(Icons.Rounded.Cancel, contentDescription = tr(isArabic, "إلغاء", "Cancel"), tint = ext.crimson)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (debt == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(tr(isArabic, "الدين غير موجود", "Debt not found"))
            }
            return@Scaffold
        }

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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(customer?.fullName ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(debt.invoiceNumber, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                            Text("${tr(isArabic, "التاريخ:", "Date:")} ${formatDateTime(debt.createdAt)}", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                            debt.dueDate?.let {
                                Text("${tr(isArabic, "الاستحقاق:", "Due:")} ${formatDateShort(it)}", style = MaterialTheme.typography.bodySmall, color = ext.gold)
                            }
                        }
                        StatusBadge(
                            text = debt.status.localized(isArabic),
                            containerColor = when (debt.status) {
                                DebtStatus.OPEN -> ext.crimsonContainer
                                DebtStatus.PARTIAL -> ext.goldContainer
                                DebtStatus.PAID -> ext.emeraldContainer
                                DebtStatus.CANCELED -> ext.subtleSurface
                            },
                            contentColor = when (debt.status) {
                                DebtStatus.OPEN -> ext.crimson
                                DebtStatus.PARTIAL -> ext.gold
                                DebtStatus.PAID -> ext.emerald
                                DebtStatus.CANCELED -> ext.mutedText
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(isArabic, "الإجمالي:", "Total:"), color = ext.mutedText)
                        Text(MoneyUtils.format(debt.totalAmountMinor, debt.currency, isArabic), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(isArabic, "المدفوع:", "Paid:"), color = ext.mutedText)
                        Text(MoneyUtils.format(debt.paidAmountMinor, debt.currency, isArabic), color = ext.emerald, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(isArabic, "المتبقي:", "Remaining:"), color = ext.mutedText)
                        Text(MoneyUtils.format(debt.remainingAmountMinor, debt.currency, isArabic), color = ext.crimson, fontWeight = FontWeight.Bold)
                    }
                    if (debt.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${tr(isArabic, "ملاحظات:", "Notes:")} ${debt.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (debt.remainingAmountMinor > 0L && debt.status != DebtStatus.CANCELED) {
                item {
                    Button(
                        onClick = onPayDebt,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.emerald)
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isArabic, "سداد دفعة لهذا الدين", "Record Payment for This Debt"))
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExportPdf(false, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(onClick = { onExportPdf(true, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(isArabic, "واتساب", "WhatsApp"))
                    }
                    OutlinedButton(onClick = { onExportPdf(false, true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(isArabic, "طباعة", "Print"))
                    }
                }
            }

            if (lineItems.isNotEmpty()) {
                item {
                    Text(tr(isArabic, "بنود الفاتورة (${lineItems.size})", "Invoice Items (${lineItems.size})"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(lineItems, key = { it.id }) { item ->
                    GlassFinanceCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.quantity} × ${MoneyUtils.format(item.unitPriceMinor, debt.currency, isArabic)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                            }
                            Text(
                                MoneyUtils.format(item.lineTotalMinor, debt.currency, isArabic),
                                fontWeight = FontWeight.Bold,
                                color = ext.gold
                            )
                        }
                    }
                }
            }

            debt.receiptImageUri?.let { uri ->
                item {
                    GlassFinanceCard {
                        Text(tr(isArabic, "صورة السند المرفقة", "Attached Receipt"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = uri,
                            contentDescription = "Receipt image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && debt != null) {
        var amtText by remember { mutableStateOf(MoneyUtils.formatEditable(debt.totalAmountMinor)) }
        var due by remember { mutableStateOf(debt.dueDate) }
        var notes by remember { mutableStateOf(debt.notes) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(tr(isArabic, "تعديل تفاصيل الدين", "Edit Debt Details")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lineItems.isEmpty()) {
                        OutlinedTextField(
                            value = amtText,
                            onValueChange = { amtText = it },
                            label = { Text(tr(isArabic, "المبلغ الإجمالي", "Total Amount")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    DatePickerField(
                        label = tr(isArabic, "تاريخ الاستحقاق", "Due Date"),
                        selectedMillis = due,
                        isArabic = isArabic,
                        onDateSelected = { due = it }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(isArabic, "ملاحظات", "Notes")) },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newMinor = MoneyUtils.parseToMinorUnits(amtText)
                    onEditDebt(newMinor, due, notes)
                    showEditDialog = false
                }) {
                    Text(tr(isArabic, "حفظ", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(tr(isArabic, "إلغاء", "Cancel"))
                }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(tr(isArabic, "إلغاء الدين واستعادة المخزون", "Cancel Debt & Restore Stock")) },
            text = {
                Text(
                    tr(
                        isArabic,
                        "سيتم تعليم الفاتورة كملغاة وإرجاع كميات المنتجات المخصومة إلى المخزون مع حفظ سجل التدقيق.",
                        "This marks the invoice as Canceled and restores deducted product stock while preserving the audit trail."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancelDebt()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.crimson)
                ) {
                    Text(tr(isArabic, "تأكيد الإلغاء", "Confirm Cancel"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(tr(isArabic, "تراجع", "Back"))
                }
            }
        )
    }
}
