package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.preferences.MerchantSettings
import com.example.domain.model.DashboardSummary
import com.example.ui.components.CurrencyLedgerCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.MonthlyFinanceChart
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateShort
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

sealed class RecentTxItem(val timestamp: Long) {
    data class DebtItem(val debt: DebtEntity, val customerName: String) : RecentTxItem(debt.createdAt)
    data class PaymentItem(val payment: PaymentEntity, val customerName: String) : RecentTxItem(payment.paymentDate)
}

@Composable
fun DashboardScreen(
    settings: MerchantSettings,
    summary: DashboardSummary,
    customers: List<CustomerEntity>,
    debts: List<DebtEntity>,
    payments: List<PaymentEntity>,
    reminders: List<ReminderEntity>,
    onAddCustomer: () -> Unit,
    onAddDebt: () -> Unit,
    onAddPayment: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    onOpenReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = settings.isArabic
    val ext = LocalDebtBookColors.current
    var chartCurrency by remember(settings.defaultCurrency) { mutableStateOf(settings.defaultCurrency) }

    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val recentTransactions = remember(debts, payments, customerMap) {
        val dItems = debts.filter { it.status != DebtStatus.CANCELED }.map {
            RecentTxItem.DebtItem(it, customerMap[it.customerId]?.fullName ?: "—")
        }
        val pItems = payments.map {
            RecentTxItem.PaymentItem(it, customerMap[it.customerId]?.fullName ?: "—")
        }
        (dItems + pItems).sortedByDescending { it.timestamp }.take(8)
    }

    val upcomingReminders = remember(reminders) {
        reminders.filter { it.status == ReminderStatus.SCHEDULED }.sortedBy { it.dueAt }.take(4)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0A1628))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.32f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xAA0A1628),
                                    Color(0xEE0A1628)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.merchantName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFD4AF37),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tr(isArabic, "دفتر التاجر المالي الذكي", "Smart Merchant Debt Ledger"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF152946),
                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "1 USD = ${settings.usdToIlsRate} ILS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF8FAFC),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroKpiChip(
                            label = tr(isArabic, "الزبائن النشطون", "Active Customers"),
                            value = summary.activeCustomersCount.toString(),
                            accent = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        HeroKpiChip(
                            label = tr(isArabic, "ديون متأخرة", "Overdue Debts"),
                            value = summary.overdueCount.toString(),
                            accent = if (summary.overdueCount > 0) Color(0xFFEF4444) else Color(0xFFD4AF37),
                            modifier = Modifier.weight(1f)
                        )
                        HeroKpiChip(
                            label = tr(isArabic, "العملة الافتراضية", "Default"),
                            value = settings.defaultCurrency.code,
                            accent = Color(0xFFD4AF37),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Rounded.PersonAdd,
                    label = tr(isArabic, "إضافة زبون", "Add Customer"),
                    accent = ext.gold,
                    onClick = onAddCustomer,
                    modifier = Modifier.weight(1f).testTag("quick_add_customer")
                )
                QuickActionButton(
                    icon = Icons.Rounded.AddCard,
                    label = tr(isArabic, "تسجيل دين", "New Debt"),
                    accent = ext.crimson,
                    onClick = onAddDebt,
                    modifier = Modifier.weight(1f).testTag("quick_add_debt")
                )
                QuickActionButton(
                    icon = Icons.Rounded.Payments,
                    label = tr(isArabic, "تسجيل دفعة", "Add Payment"),
                    accent = ext.emerald,
                    onClick = onAddPayment,
                    modifier = Modifier.weight(1f).testTag("quick_add_payment")
                )
            }
        }

        // Separate Currency Summaries (ILS and USD never mixed!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CurrencyLedgerCard(
                    currency = CurrencyCode.ILS,
                    outstandingMinor = summary.outstandingIlsMinor,
                    collectedMinor = summary.collectedIlsMinor,
                    overdueMinor = summary.overdueIlsMinor,
                    isArabic = isArabic
                )
                CurrencyLedgerCard(
                    currency = CurrencyCode.USD,
                    outstandingMinor = summary.outstandingUsdMinor,
                    collectedMinor = summary.collectedUsdMinor,
                    overdueMinor = summary.overdueUsdMinor,
                    isArabic = isArabic
                )
            }
        }

        // Monthly Chart with currency switch
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = chartCurrency == CurrencyCode.ILS,
                        onClick = { chartCurrency = CurrencyCode.ILS },
                        label = { Text(CurrencyCode.ILS.label(isArabic)) }
                    )
                    FilterChip(
                        selected = chartCurrency == CurrencyCode.USD,
                        onClick = { chartCurrency = CurrencyCode.USD },
                        label = { Text(CurrencyCode.USD.label(isArabic)) }
                    )
                }
                MonthlyFinanceChart(
                    stats = summary.monthlyStats,
                    currency = chartCurrency,
                    isArabic = isArabic
                )
            }
        }

        // Upcoming Payment Reminders
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(isArabic, "تذكيرات الدفع القادمة", "Upcoming Payment Reminders"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onOpenReminders) {
                    Text(tr(isArabic, "عرض الكل", "View All"))
                }
            }
        }

        if (upcomingReminders.isEmpty()) {
            item {
                GlassFinanceCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = ext.gold)
                        Text(
                            text = tr(
                                isArabic,
                                "لا توجد تذكيرات مجدولة حالياً. أضف تاريخ استحقاق عند تسجيل أي دين.",
                                "No scheduled reminders yet. Set a due date when creating a debt."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.mutedText
                        )
                    }
                }
            }
        } else {
            items(upcomingReminders, key = { it.id }) { rem ->
                val isLate = rem.dueAt < System.currentTimeMillis()
                GlassFinanceCard(
                    onClick = onOpenReminders,
                    borderColor = if (isLate) ext.crimson.copy(alpha = 0.6f) else ext.cardBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rem.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(rem.message, style = MaterialTheme.typography.bodySmall, color = ext.mutedText, maxLines = 2)
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            text = formatDateShort(rem.dueAt),
                            containerColor = if (isLate) ext.crimsonContainer else ext.goldContainer,
                            contentColor = if (isLate) ext.crimson else ext.gold
                        )
                    }
                }
            }
        }

        // Recent Transactions
        item {
            Text(
                text = tr(isArabic, "أحدث المعاملات المالية", "Recent Transactions"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (recentTransactions.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Rounded.ReceiptLong,
                    title = tr(isArabic, "سجلك المالي جاهز للبدء", "Your Financial Ledger Is Ready"),
                    subtitle = tr(
                        isArabic,
                        "أضف أول زبون وسجّل أول دين أو دفعة لمتابعة أرصدتك بدقة تامة.",
                        "Add your first customer and record a debt or payment to track balances accurately."
                    ),
                    actionLabel = tr(isArabic, "إضافة زبون الآن", "Add First Customer"),
                    onAction = onAddCustomer
                )
            }
        } else {
            items(recentTransactions, key = {
                when (it) {
                    is RecentTxItem.DebtItem -> "d_${it.debt.id}"
                    is RecentTxItem.PaymentItem -> "p_${it.payment.id}"
                }
            }) { item ->
                when (item) {
                    is RecentTxItem.DebtItem -> {
                        val d = item.debt
                        GlassFinanceCard(onClick = { onOpenDebt(d.id) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(ext.crimsonContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = ext.crimson)
                                    }
                                    Column {
                                        Text(item.customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${d.invoiceNumber} • ${formatDateTime(d.createdAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.mutedText
                                        )
                                    }
                                }
                                Text(
                                    text = "+${MoneyUtils.format(d.totalAmountMinor, d.currency, isArabic)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ext.crimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is RecentTxItem.PaymentItem -> {
                        val p = item.payment
                        val isReversed = p.status == PaymentStatus.REVERSED
                        GlassFinanceCard(onClick = { onOpenPayment(p.id) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isReversed) ext.crimsonContainer else ext.emeraldContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isReversed) Icons.Rounded.WarningAmber else Icons.Rounded.TrendingDown,
                                            contentDescription = null,
                                            tint = if (isReversed) ext.crimson else ext.emerald
                                        )
                                    }
                                    Column {
                                        Text(item.customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${p.receiptNumber} • ${formatDateTime(p.paymentDate)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.mutedText
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = MoneyUtils.format(p.paymentAmountMinor, p.paymentCurrency, isArabic),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isReversed) ext.mutedText else ext.emerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (p.paymentCurrency != p.targetCurrency) {
                                        Text(
                                            text = "= ${MoneyUtils.format(p.convertedAmountMinor, p.targetCurrency, isArabic)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.gold
                                        )
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

@Composable
private fun HeroKpiChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
