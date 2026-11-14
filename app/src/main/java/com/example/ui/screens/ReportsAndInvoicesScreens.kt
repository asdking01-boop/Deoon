package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.ReceiptLong
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.CustomerFinancialSummary
import com.example.domain.model.DashboardSummary
import com.example.domain.model.isOverdue
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.MetricTile
import com.example.ui.components.MonthlyFinanceChart
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    debts: List<DebtEntity>,
    payments: List<PaymentEntity>,
    customers: List<CustomerEntity>,
    isArabic: Boolean,
    onBack: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    onExportInvoicePdf: (DebtEntity, CustomerEntity, shareWhatsApp: Boolean, print: Boolean) -> Unit,
    onExportReceiptPdf: (PaymentEntity, CustomerEntity, shareWhatsApp: Boolean, print: Boolean) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var tab by remember { mutableIntStateOf(0) }
    val customerMap = remember(customers) { customers.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "الفواتير والإيصالات", "Invoices & Receipts")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr(isArabic, "فواتير الديون", "Debt Invoices")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(isArabic, "إيصالات الدفع", "Payment Receipts")) })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) {
                    if (debts.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.ReceiptLong,
                                title = tr(isArabic, "لا توجد فواتير بعد", "No Invoices Yet"),
                                subtitle = tr(isArabic, "تظهر فواتير الديون هنا فور تسجيلها.", "Debt invoices appear here once created.")
                            )
                        }
                    } else {
                        items(debts, key = { it.id }) { debt ->
                            val customer = customerMap[debt.customerId]
                            GlassFinanceCard(onClick = { onOpenDebt(debt.id) }) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(debt.invoiceNumber, fontWeight = FontWeight.Bold)
                                        Text(customer?.fullName ?: "—", style = MaterialTheme.typography.bodyMedium)
                                        Text(formatDateTime(debt.createdAt), style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                    }
                                    Text(
                                        MoneyUtils.format(debt.totalAmountMinor, debt.currency, isArabic),
                                        fontWeight = FontWeight.Bold,
                                        color = ext.crimson
                                    )
                                }
                                if (customer != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onExportInvoicePdf(debt, customer, false, false) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("PDF")
                                        }
                                        OutlinedButton(onClick = { onExportInvoicePdf(debt, customer, true, false) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(isArabic, "واتساب", "WhatsApp"))
                                        }
                                        OutlinedButton(onClick = { onExportInvoicePdf(debt, customer, false, true) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(isArabic, "طباعة", "Print"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (payments.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.ReceiptLong,
                                title = tr(isArabic, "لا توجد إيصالات دفع بعد", "No Payment Receipts Yet"),
                                subtitle = tr(isArabic, "تظهر إيصالات الدفع هنا فور تسجيلها.", "Payment receipts appear here once recorded.")
                            )
                        }
                    } else {
                        items(payments, key = { it.id }) { pay ->
                            val customer = customerMap[pay.customerId]
                            GlassFinanceCard(onClick = { onOpenPayment(pay.id) }) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pay.receiptNumber, fontWeight = FontWeight.Bold)
                                        Text(customer?.fullName ?: "—", style = MaterialTheme.typography.bodyMedium)
                                        Text(formatDateTime(pay.paymentDate), style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                    }
                                    Text(
                                        MoneyUtils.format(pay.paymentAmountMinor, pay.paymentCurrency, isArabic),
                                        fontWeight = FontWeight.Bold,
                                        color = ext.emerald
                                    )
                                }
                                if (customer != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onExportReceiptPdf(pay, customer, false, false) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("PDF")
                                        }
                                        OutlinedButton(onClick = { onExportReceiptPdf(pay, customer, true, false) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(isArabic, "واتساب", "WhatsApp"))
                                        }
                                        OutlinedButton(onClick = { onExportReceiptPdf(pay, customer, false, true) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(isArabic, "طباعة", "Print"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ReportRangePreset { ALL, TODAY, WEEK, MONTH, CUSTOM }
enum class ReportTxType { ALL, DEBTS, PAYMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    dashboardSummary: DashboardSummary,
    customerSummaries: List<CustomerFinancialSummary>,
    debts: List<DebtEntity>,
    lineItems: List<DebtLineItemEntity>,
    payments: List<PaymentEntity>,
    products: List<ProductEntity>,
    defaultCurrency: CurrencyCode,
    isArabic: Boolean,
    onBack: () -> Unit,
    onExportReportPdf: (title: String, rows: List<Pair<String, String>>) -> Unit,
    onExportReportCsv: (uri: Uri, headers: List<String>, rows: List<List<String>>) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var currency by remember { mutableStateOf(defaultCurrency) }
    var rangePreset by remember { mutableStateOf(ReportRangePreset.MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var custDropdown by remember { mutableStateOf(false) }
    var txType by remember { mutableStateOf(ReportTxType.ALL) }

    val now = System.currentTimeMillis()
    val (rangeStart, rangeEnd) = remember(rangePreset, customStart, customEnd, now) {
        val cal = Calendar.getInstance()
        when (rangePreset) {
            ReportRangePreset.ALL -> 0L to Long.MAX_VALUE
            ReportRangePreset.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis to now
            }
            ReportRangePreset.WEEK -> (now - 7L * 86_400_000L) to now
            ReportRangePreset.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.timeInMillis to now
            }
            ReportRangePreset.CUSTOM -> (customStart ?: 0L) to (customEnd?.plus(86_399_999L) ?: Long.MAX_VALUE)
        }
    }

    val filteredDebts = remember(debts, currency, selectedCustomerId, rangeStart, rangeEnd) {
        debts.filter { d ->
            d.status != DebtStatus.CANCELED &&
                d.currency == currency &&
                (selectedCustomerId == null || d.customerId == selectedCustomerId) &&
                d.createdAt in rangeStart..rangeEnd
        }
    }

    val filteredPayments = remember(payments, currency, selectedCustomerId, rangeStart, rangeEnd) {
        payments.filter { p ->
            p.status == PaymentStatus.COMPLETED &&
                p.targetCurrency == currency &&
                (selectedCustomerId == null || p.customerId == selectedCustomerId) &&
                p.paymentDate in rangeStart..rangeEnd
        }
    }

    // Period collections (Daily, Weekly, Monthly)
    val startToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
    }
    val startWeek = now - 7L * 86_400_000L
    val startMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        }.timeInMillis
    }

    val completedInCurrency = payments.filter { it.status == PaymentStatus.COMPLETED && it.targetCurrency == currency }
    val dailyCollected = completedInCurrency.filter { it.paymentDate >= startToday }.sumOf { it.convertedAmountMinor }
    val weeklyCollected = completedInCurrency.filter { it.paymentDate >= startWeek }.sumOf { it.convertedAmountMinor }
    val monthlyCollected = completedInCurrency.filter { it.paymentDate >= startMonth }.sumOf { it.convertedAmountMinor }

    val outstandingTotal = filteredDebts.sumOf { it.remainingAmountMinor }
    val overdueTotal = filteredDebts.filter { it.isOverdue(now) }.sumOf { it.remainingAmountMinor }
    val filteredCollectedTotal = filteredPayments.sumOf { it.convertedAmountMinor }
    val salesVolumeTotal = filteredDebts.sumOf { it.totalAmountMinor }

    // Profit Calculation (strictly separated from cash collections and receivables!)
    val filteredDebtIds = remember(filteredDebts) { filteredDebts.map { it.id }.toSet() }
    val filteredLineItems = remember(lineItems, filteredDebtIds) {
        lineItems.filter { it.debtId in filteredDebtIds }
    }
    val estimatedGrossProfitMinor = filteredLineItems.sumOf { item ->
        if (item.purchasePriceMinor > 0L) {
            (item.unitPriceMinor - item.purchasePriceMinor) * item.quantity.toLong()
        } else 0L
    }

    // Inventory valuation in selected currency
    val inventoryCostValuation = products.filter { it.currency == currency }
        .sumOf { it.purchasePriceMinor * it.availableQty.toLong() }
    val inventoryRetailValuation = products.filter { it.currency == currency }
        .sumOf { it.sellingPriceMinor * it.availableQty.toLong() }

    // Product performance
    val productPerf = remember(filteredLineItems) {
        filteredLineItems.groupBy { it.productName }
            .mapValues { (_, list) -> list.sumOf { it.quantity } to list.sumOf { it.lineTotalMinor } }
            .entries.sortedByDescending { it.value.second }.take(6)
    }

    // Customer debt rankings in selected currency
    val topDebtors = remember(customerSummaries, currency) {
        customerSummaries.map {
            val bal = if (currency == CurrencyCode.ILS) it.outstandingIlsMinor else it.outstandingUsdMinor
            it.customer.fullName to bal
        }.filter { it.second > 0L }.sortedByDescending { it.second }.take(8)
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val headers = listOf("Metric", "Value (${currency.code})")
            val rows = listOf(
                listOf("Daily Collections", MoneyUtils.formatNumber(dailyCollected)),
                listOf("Weekly Collections", MoneyUtils.formatNumber(weeklyCollected)),
                listOf("Monthly Collections", MoneyUtils.formatNumber(monthlyCollected)),
                listOf("Filtered Sales / Debts", MoneyUtils.formatNumber(salesVolumeTotal)),
                listOf("Filtered Cash Collected", MoneyUtils.formatNumber(filteredCollectedTotal)),
                listOf("Outstanding Receivables", MoneyUtils.formatNumber(outstandingTotal)),
                listOf("Overdue Receivables", MoneyUtils.formatNumber(overdueTotal)),
                listOf("Estimated Itemized Gross Profit (Separate)", MoneyUtils.formatNumber(estimatedGrossProfitMinor)),
                listOf("Inventory Valuation (Cost)", MoneyUtils.formatNumber(inventoryCostValuation)),
                listOf("Inventory Valuation (Retail)", MoneyUtils.formatNumber(inventoryRetailValuation))
            )
            onExportReportCsv(uri, headers, rows)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "التقارير المالية", "Financial Reports")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val title = tr(isArabic, "التقرير المالي (${currency.code})", "Financial Report (${currency.code})")
                        val rows = listOf(
                            tr(isArabic, "التحصيل اليومي", "Daily Collections") to MoneyUtils.format(dailyCollected, currency, isArabic),
                            tr(isArabic, "التحصيل الأسبوعي", "Weekly Collections") to MoneyUtils.format(weeklyCollected, currency, isArabic),
                            tr(isArabic, "التحصيل الشهري", "Monthly Collections") to MoneyUtils.format(monthlyCollected, currency, isArabic),
                            tr(isArabic, "إجمالي المبيعات/الديون (ضمن الفلتر)", "Filtered Sales/Debts") to MoneyUtils.format(salesVolumeTotal, currency, isArabic),
                            tr(isArabic, "التحصيل النقدي (ضمن الفلتر)", "Filtered Collections") to MoneyUtils.format(filteredCollectedTotal, currency, isArabic),
                            tr(isArabic, "الذمم القائمة", "Outstanding Receivables") to MoneyUtils.format(outstandingTotal, currency, isArabic),
                            tr(isArabic, "الذمم المتأخرة", "Overdue Receivables") to MoneyUtils.format(overdueTotal, currency, isArabic),
                            tr(isArabic, "هامش الربح التقديري (منفصل)", "Est. Gross Profit (Separate)") to MoneyUtils.format(estimatedGrossProfitMinor, currency, isArabic),
                            tr(isArabic, "قيمة المخزون (تكلفة)", "Inventory Cost Value") to MoneyUtils.format(inventoryCostValuation, currency, isArabic)
                        )
                        onExportReportPdf(title, rows)
                    }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF")
                    }
                    IconButton(onClick = { exportCsvLauncher.launch("financial_report_${currency.code}.csv") }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Excel CSV")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filters
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "فلاتر التقرير", "Report Filters"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = currency == CurrencyCode.ILS, onClick = { currency = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                        FilterChip(selected = currency == CurrencyCode.USD, onClick = { currency = CurrencyCode.USD }, label = { Text("USD $") })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = rangePreset == ReportRangePreset.TODAY, onClick = { rangePreset = ReportRangePreset.TODAY }, label = { Text(tr(isArabic, "اليوم", "Today")) })
                        FilterChip(selected = rangePreset == ReportRangePreset.WEEK, onClick = { rangePreset = ReportRangePreset.WEEK }, label = { Text(tr(isArabic, "أسبوع", "Week")) })
                        FilterChip(selected = rangePreset == ReportRangePreset.MONTH, onClick = { rangePreset = ReportRangePreset.MONTH }, label = { Text(tr(isArabic, "شهر", "Month")) })
                        FilterChip(selected = rangePreset == ReportRangePreset.ALL, onClick = { rangePreset = ReportRangePreset.ALL }, label = { Text(tr(isArabic, "الكل", "All")) })
                        FilterChip(selected = rangePreset == ReportRangePreset.CUSTOM, onClick = { rangePreset = ReportRangePreset.CUSTOM }, label = { Text(tr(isArabic, "مخصص", "Custom")) })
                    }
                    if (rangePreset == ReportRangePreset.CUSTOM) {
                        Spacer(Modifier.height(6.dp))
                        DatePickerField(tr(isArabic, "من تاريخ", "From"), customStart, isArabic, { customStart = it })
                        Spacer(Modifier.height(6.dp))
                        DatePickerField(tr(isArabic, "إلى تاريخ", "To"), customEnd, isArabic, { customEnd = it })
                    }
                    Spacer(Modifier.height(6.dp))
                    ExposedDropdownMenuBox(expanded = custDropdown, onExpandedChange = { custDropdown = !custDropdown }) {
                        val cLabel = customerSummaries.firstOrNull { it.customer.id == selectedCustomerId }?.customer?.fullName
                            ?: tr(isArabic, "جميع الزبائن", "All Customers")
                        OutlinedTextField(
                            value = cLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(isArabic, "الزبون", "Customer")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = custDropdown, onDismissRequest = { custDropdown = false }) {
                            DropdownMenuItem(text = { Text(tr(isArabic, "جميع الزبائن", "All Customers")) }, onClick = {
                                selectedCustomerId = null; custDropdown = false
                            })
                            customerSummaries.forEach { cs ->
                                DropdownMenuItem(text = { Text(cs.customer.fullName) }, onClick = {
                                    selectedCustomerId = cs.customer.id; custDropdown = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = txType == ReportTxType.ALL, onClick = { txType = ReportTxType.ALL }, label = { Text(tr(isArabic, "الكل", "All")) })
                        FilterChip(selected = txType == ReportTxType.DEBTS, onClick = { txType = ReportTxType.DEBTS }, label = { Text(tr(isArabic, "الديون", "Debts")) })
                        FilterChip(selected = txType == ReportTxType.PAYMENTS, onClick = { txType = ReportTxType.PAYMENTS }, label = { Text(tr(isArabic, "الدفعات", "Payments")) })
                    }
                }
            }

            // Cash Collections (Daily / Weekly / Monthly)
            if (txType != ReportTxType.DEBTS) {
                item {
                    GlassFinanceCard {
                        Text(tr(isArabic, "تقارير التحصيل النقدي (${currency.code})", "Cash Collections (${currency.code})"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(tr(isArabic, "اليومي", "Daily"), MoneyUtils.format(dailyCollected, currency, isArabic), ext.emerald, ext.emeraldContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                            MetricTile(tr(isArabic, "الأسبوعي", "Weekly"), MoneyUtils.format(weeklyCollected, currency, isArabic), ext.emerald, ext.emeraldContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(tr(isArabic, "الشهري", "Monthly"), MoneyUtils.format(monthlyCollected, currency, isArabic), ext.emerald, ext.emeraldContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                            MetricTile(tr(isArabic, "ضمن الفلتر", "Filtered"), MoneyUtils.format(filteredCollectedTotal, currency, isArabic), ext.gold, ext.goldContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                        }
                    }
                }
            }

            // Receivables & Sales (separate from Profit!)
            if (txType != ReportTxType.PAYMENTS) {
                item {
                    GlassFinanceCard {
                        Text(tr(isArabic, "الذمم والمبيعات (${currency.code})", "Receivables & Sales (${currency.code})"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(tr(isArabic, "ذمم قائمة", "Outstanding"), MoneyUtils.format(outstandingTotal, currency, isArabic), ext.crimson, ext.crimsonContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                            MetricTile(tr(isArabic, "ذمم متأخرة", "Overdue"), MoneyUtils.format(overdueTotal, currency, isArabic), ext.crimson, ext.crimsonContainer.copy(alpha = 0.5f), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        MetricTile(tr(isArabic, "حجم المبيعات الآجلة (ضمن الفلتر)", "Filtered Credit Sales"), MoneyUtils.format(salesVolumeTotal, currency, isArabic), ext.gold, ext.goldContainer.copy(alpha = 0.5f), Modifier.fillMaxWidth())
                    }
                }
            }

            // Separate Profit & Inventory Valuation Card
            item {
                GlassFinanceCard(borderColor = ext.gold.copy(alpha = 0.6f)) {
                    Text(
                        tr(isArabic, "الربحية وتقييم المخزون (منفصل عن التحصيل والذمم)", "Profit & Inventory Valuation (Separate from Cash & Receivables)"),
                        style = MaterialTheme.typography.titleSmall,
                        color = ext.gold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    MetricTile(
                        label = tr(isArabic, "إجمالي ربح الفواتير المفصّلة", "Itemized Gross Profit"),
                        value = MoneyUtils.format(estimatedGrossProfitMinor, currency, isArabic),
                        accent = ext.emerald,
                        container = ext.emeraldContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(tr(isArabic, "المخزون (تكلفة)", "Stock (Cost)"), MoneyUtils.format(inventoryCostValuation, currency, isArabic), ext.gold, ext.goldContainer.copy(alpha = 0.45f), Modifier.weight(1f))
                        MetricTile(tr(isArabic, "المخزون (بيع)", "Stock (Retail)"), MoneyUtils.format(inventoryRetailValuation, currency, isArabic), ext.gold, ext.goldContainer.copy(alpha = 0.45f), Modifier.weight(1f))
                    }
                }
            }

            // Monthly Chart
            item {
                MonthlyFinanceChart(stats = dashboardSummary.monthlyStats, currency = currency, isArabic = isArabic)
            }

            // Customer Debt Rankings
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "ترتيب الزبائن حسب الدين المستحق", "Customer Debt Rankings"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (topDebtors.isEmpty()) {
                        Text(tr(isArabic, "لا توجد ذمم قائمة بهذه العملة", "No open receivables in this currency"), color = ext.mutedText)
                    } else {
                        topDebtors.forEachIndexed { idx, (name, bal) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${idx + 1}. $name", fontWeight = FontWeight.Medium)
                                Text(MoneyUtils.format(bal, currency, isArabic), color = ext.crimson, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Product Performance
            if (productPerf.isNotEmpty()) {
                item {
                    GlassFinanceCard {
                        Text(tr(isArabic, "أداء المنتجات الأكثر مبيعاً", "Top Product Performance"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        productPerf.forEach { (name, pair) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$name (${pair.first}×)")
                                Text(MoneyUtils.format(pair.second, currency, isArabic), color = ext.gold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
