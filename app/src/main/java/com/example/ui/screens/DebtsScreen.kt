package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ReceiptLong
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.DraftLineItem
import com.example.domain.model.isOverdue
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateShort
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

enum class DebtFilter { ALL, OPEN_PARTIAL, OVERDUE, PAID, CANCELED }

@Composable
fun DebtsScreen(
    debts: List<DebtEntity>,
    customers: List<CustomerEntity>,
    products: List<ProductEntity>,
    defaultCurrency: CurrencyCode,
    isArabic: Boolean,
    onOpenDebt: (String) -> Unit,
    onCreateDebt: (
        customerId: String,
        currency: CurrencyCode,
        manualAmountMinor: Long?,
        lineItems: List<DraftLineItem>,
        discountMinor: Long,
        taxMinor: Long,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?
    ) -> Unit,
    preselectedCustomerId: String? = null,
    showAddDialogInitially: Boolean = false,
    onAddDialogConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DebtFilter.ALL) }
    var currencyFilter by remember { mutableStateOf<CurrencyCode?>(null) }
    var showAddDialog by remember(showAddDialogInitially) {
        mutableStateOf(showAddDialogInitially).also {
            if (showAddDialogInitially) onAddDialogConsumed()
        }
    }

    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val filtered = remember(debts, search, filter, currencyFilter, customerMap) {
        val now = System.currentTimeMillis()
        debts.filter { d ->
            val cName = customerMap[d.customerId]?.fullName ?: ""
            val matchQuery = search.isBlank() ||
                d.invoiceNumber.contains(search, ignoreCase = true) ||
                cName.contains(search, ignoreCase = true) ||
                d.notes.contains(search, ignoreCase = true)
            val matchStatus = when (filter) {
                DebtFilter.ALL -> true
                DebtFilter.OPEN_PARTIAL -> d.status == DebtStatus.OPEN || d.status == DebtStatus.PARTIAL
                DebtFilter.OVERDUE -> d.isOverdue(now)
                DebtFilter.PAID -> d.status == DebtStatus.PAID
                DebtFilter.CANCELED -> d.status == DebtStatus.CANCELED
            }
            val matchCurrency = currencyFilter == null || d.currency == currencyFilter
            matchQuery && matchStatus && matchCurrency
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
                    tr(isArabic, "سجل الديون (${filtered.size})", "Debts (${filtered.size})"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.crimson),
                    modifier = Modifier.testTag("add_debt_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(isArabic, "دين جديد", "New Debt"))
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr(isArabic, "ابحث برقم الفاتورة أو اسم الزبون...", "Search invoice # or customer...")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filter == DebtFilter.ALL, onClick = { filter = DebtFilter.ALL }, label = { Text(tr(isArabic, "الكل", "All")) })
                FilterChip(selected = filter == DebtFilter.OPEN_PARTIAL, onClick = { filter = DebtFilter.OPEN_PARTIAL }, label = { Text(tr(isArabic, "مستحق", "Open")) })
                FilterChip(selected = filter == DebtFilter.OVERDUE, onClick = { filter = DebtFilter.OVERDUE }, label = { Text(tr(isArabic, "متأخر", "Overdue")) })
                FilterChip(selected = filter == DebtFilter.PAID, onClick = { filter = DebtFilter.PAID }, label = { Text(tr(isArabic, "مسدد", "Paid")) })
                FilterChip(selected = filter == DebtFilter.CANCELED, onClick = { filter = DebtFilter.CANCELED }, label = { Text(tr(isArabic, "ملغى", "Canceled")) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currencyFilter == null, onClick = { currencyFilter = null }, label = { Text(tr(isArabic, "كل العملات", "All Currencies")) })
                FilterChip(selected = currencyFilter == CurrencyCode.ILS, onClick = { currencyFilter = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                FilterChip(selected = currencyFilter == CurrencyCode.USD, onClick = { currencyFilter = CurrencyCode.USD }, label = { Text("USD $") })
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
                        icon = Icons.Rounded.ReceiptLong,
                        title = tr(isArabic, "لا توجد ديون مسجلة", "No Debts Recorded"),
                        subtitle = tr(
                            isArabic,
                            "سجّل أول دين بالشيكل أو الدولار مع إمكانية اختيار منتجات من المخزون وتحديد موعد استحقاق.",
                            "Record a new debt in ILS or USD, optionally selecting catalog products and a due date."
                        ),
                        actionLabel = tr(isArabic, "تسجيل دين", "New Debt"),
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { debt ->
                    val customerName = customerMap[debt.customerId]?.fullName ?: "—"
                    val overdue = debt.isOverdue()
                    GlassFinanceCard(
                        onClick = { onOpenDebt(debt.id) },
                        borderColor = if (overdue) ext.crimson.copy(alpha = 0.7f) else ext.cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${debt.invoiceNumber} • ${formatDateTime(debt.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                                debt.dueDate?.let { due ->
                                    Text(
                                        "${tr(isArabic, "الاستحقاق:", "Due:")} ${formatDateShort(due)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (overdue) ext.crimson else ext.mutedText
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    MoneyUtils.format(debt.totalAmountMinor, debt.currency, isArabic),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusBadge(
                                    text = if (overdue) tr(isArabic, "متأخر", "Overdue")
                                    else debt.status.localized(isArabic),
                                    containerColor = when {
                                        overdue || debt.status == DebtStatus.OPEN -> ext.crimsonContainer
                                        debt.status == DebtStatus.PARTIAL -> ext.goldContainer
                                        debt.status == DebtStatus.PAID -> ext.emeraldContainer
                                        else -> ext.subtleSurface
                                    },
                                    contentColor = when {
                                        overdue || debt.status == DebtStatus.OPEN -> ext.crimson
                                        debt.status == DebtStatus.PARTIAL -> ext.gold
                                        debt.status == DebtStatus.PAID -> ext.emerald
                                        else -> ext.mutedText
                                    }
                                )
                                if (debt.remainingAmountMinor > 0L && debt.status == DebtStatus.PARTIAL) {
                                    Text(
                                        "${tr(isArabic, "متبقي:", "Rem:")} ${MoneyUtils.format(debt.remainingAmountMinor, debt.currency, isArabic)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.crimson
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
        AddDebtDialog(
            customers = customers.filter { !it.isArchived },
            products = products,
            defaultCurrency = defaultCurrency,
            preselectedCustomerId = preselectedCustomerId,
            isArabic = isArabic,
            onDismiss = { showAddDialog = false },
            onConfirm = { cId, curr, manualAmt, items, disc, tax, due, notes, img ->
                onCreateDebt(cId, curr, manualAmt, items, disc, tax, due, notes, img)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    customers: List<CustomerEntity>,
    products: List<ProductEntity>,
    defaultCurrency: CurrencyCode,
    preselectedCustomerId: String?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        customerId: String,
        currency: CurrencyCode,
        manualAmountMinor: Long?,
        lineItems: List<DraftLineItem>,
        discountMinor: Long,
        taxMinor: Long,
        dueDate: Long?,
        notes: String,
        receiptImageUri: String?
    ) -> Unit
) {
    var selectedCustomer by remember {
        mutableStateOf(
            customers.firstOrNull { it.id == preselectedCustomerId } ?: customers.firstOrNull()
        )
    }
    var customerExpanded by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var useCatalogItems by remember { mutableStateOf(false) }
    var manualAmountText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var taxText by remember { mutableStateOf("0") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<String?>(null) }

    val draftItems = remember { mutableStateListOf<DraftLineItem>() }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(products.firstOrNull()) }
    var productExpanded by remember { mutableStateOf(false) }
    var customItemName by remember { mutableStateOf("") }
    var customItemPrice by remember { mutableStateOf("") }
    var itemQtyText by remember { mutableStateOf("1") }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) receiptUri = uri.toString()
    }

    val subtotalMinor = if (useCatalogItems) {
        draftItems.sumOf { it.lineTotalMinor }
    } else {
        MoneyUtils.parseToMinorUnits(manualAmountText) ?: 0L
    }
    val discountMinor = MoneyUtils.parseToMinorUnits(discountText, allowZero = true) ?: 0L
    val taxMinor = MoneyUtils.parseToMinorUnits(taxText, allowZero = true) ?: 0L
    val finalTotalMinor = (subtotalMinor - discountMinor + taxMinor).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(isArabic, "تسجيل دين / فاتورة جديدة", "New Debt / Invoice")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customers.isEmpty()) {
                    Text(
                        tr(isArabic, "يرجى إضافة زبون أولاً قبل تسجيل الدين.", "Please add a customer first."),
                        color = LocalDebtBookColors.current.crimson
                    )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = customerExpanded,
                            onDismissRequest = { customerExpanded = false }
                        ) {
                            customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.fullName} (${c.phone})") },
                                    onClick = {
                                        selectedCustomer = c
                                        customerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currency == CurrencyCode.ILS,
                            onClick = { currency = CurrencyCode.ILS },
                            label = { Text(CurrencyCode.ILS.label(isArabic)) }
                        )
                        FilterChip(
                            selected = currency == CurrencyCode.USD,
                            onClick = { currency = CurrencyCode.USD },
                            label = { Text(CurrencyCode.USD.label(isArabic)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(isArabic, "فاتورة مفصّلة بالمنتجات", "Itemized Catalog Invoice"))
                        Switch(checked = useCatalogItems, onCheckedChange = { useCatalogItems = it })
                    }

                    if (!useCatalogItems) {
                        OutlinedTextField(
                            value = manualAmountText,
                            onValueChange = { manualAmountText = it },
                            label = { Text(tr(isArabic, "مبلغ الدين (${currency.symbol}) *", "Debt Amount (${currency.code}) *")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("debt_amount_input")
                        )
                    } else {
                        if (products.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = productExpanded,
                                onExpandedChange = { productExpanded = !productExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedProduct?.let { "${it.name} (${it.availableQty})" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(tr(isArabic, "اختر من الكتالوج", "Pick Catalog Product")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = productExpanded,
                                    onDismissRequest = { productExpanded = false }
                                ) {
                                    products.forEach { p ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${p.name} — ${MoneyUtils.format(p.sellingPriceMinor, p.currency, isArabic)} (${tr(isArabic, "متاح:", "Qty:")} ${p.availableQty})")
                                            },
                                            onClick = {
                                                selectedProduct = p
                                                productExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = itemQtyText,
                                    onValueChange = { itemQtyText = it },
                                    label = { Text(tr(isArabic, "الكمية", "Qty")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(onClick = {
                                    val p = selectedProduct ?: return@Button
                                    val qty = itemQtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    if (qty <= p.availableQty) {
                                        draftItems += DraftLineItem(
                                            productId = p.id,
                                            productName = p.name,
                                            purchasePriceMinor = p.purchasePriceMinor,
                                            unitPriceMinor = p.sellingPriceMinor,
                                            quantity = qty
                                        )
                                    }
                                }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Text(tr(isArabic, "إضافة", "Add"))
                                }
                            }
                        }

                        // Optional ad-hoc service/item row
                        OutlinedTextField(
                            value = customItemName,
                            onValueChange = { customItemName = it },
                            label = { Text(tr(isArabic, "أو بند خدمة/منتج مخصص", "Or Custom Item / Service")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customItemPrice,
                                onValueChange = { customItemPrice = it },
                                label = { Text(tr(isArabic, "السعر", "Unit Price")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedButton(onClick = {
                                val price = MoneyUtils.parseToMinorUnits(customItemPrice) ?: return@OutlinedButton
                                val qty = itemQtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                if (customItemName.isNotBlank()) {
                                    draftItems += DraftLineItem(
                                        productId = null,
                                        productName = customItemName.trim(),
                                        unitPriceMinor = price,
                                        quantity = qty
                                    )
                                    customItemName = ""
                                    customItemPrice = ""
                                }
                            }) {
                                Text(tr(isArabic, "أضف بند", "Add Line"))
                            }
                        }

                        draftItems.forEachIndexed { idx, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${line.productName} (${line.quantity} × ${MoneyUtils.formatNumber(line.unitPriceMinor)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    MoneyUtils.format(line.lineTotalMinor, currency, isArabic),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { draftItems.removeAt(idx) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text(tr(isArabic, "خصم", "Discount")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = taxText,
                            onValueChange = { taxText = it },
                            label = { Text(tr(isArabic, "ضريبة", "Tax")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Text(
                        "${tr(isArabic, "الإجمالي النهائي:", "Final Total:")} ${MoneyUtils.format(finalTotalMinor, currency, isArabic)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalDebtBookColors.current.gold,
                        fontWeight = FontWeight.Bold
                    )

                    DatePickerField(
                        label = tr(isArabic, "تاريخ الاستحقاق", "Due Date"),
                        selectedMillis = dueDate,
                        isArabic = isArabic,
                        onDateSelected = { dueDate = it }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(isArabic, "ملاحظات", "Notes")) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (receiptUri == null) tr(isArabic, "إرفاق صورة سند/فاتورة (اختياري)", "Attach Receipt Image (Optional)")
                            else tr(isArabic, "تم إرفاق صورة ✓", "Image Attached ✓")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    onConfirm(
                        cust.id,
                        currency,
                        if (useCatalogItems) null else subtotalMinor,
                        if (useCatalogItems) draftItems.toList() else emptyList(),
                        discountMinor,
                        taxMinor,
                        dueDate,
                        notes,
                        receiptUri
                    )
                },
                enabled = selectedCustomer != null && finalTotalMinor > 0L,
                modifier = Modifier.testTag("save_debt_button")
            ) {
                Text(tr(isArabic, "اعتماد الدين", "Save Debt"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(isArabic, "إلغاء", "Cancel"))
            }
        }
    )
}
