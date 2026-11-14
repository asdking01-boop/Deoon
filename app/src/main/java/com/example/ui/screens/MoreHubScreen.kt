package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

data class MoreMenuItem(
    val tag: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Composable
fun MoreHubScreen(
    isArabic: Boolean,
    lowStockCount: Int,
    pendingRemindersCount: Int,
    onOpenProducts: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuditLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    val itemsList = listOf(
        MoreMenuItem(
            tag = "more_products",
            title = tr(isArabic, "المنتجات والمخزون", "Products & Inventory"),
            subtitle = tr(isArabic, "إدارة الكتالوج، الأسعار، واستيراد CSV", "Catalog, pricing, stock & CSV import/export"),
            icon = Icons.Rounded.Inventory2,
            accent = ext.gold,
            badge = if (lowStockCount > 0) tr(isArabic, "نقص: $lowStockCount", "Low: $lowStockCount") else null,
            onClick = onOpenProducts
        ),
        MoreMenuItem(
            tag = "more_invoices",
            title = tr(isArabic, "الفواتير والإيصالات", "Invoices & Receipts"),
            subtitle = tr(isArabic, "عرض وطباعة ومشاركة ملفات PDF", "View, print, and share Arabic PDFs"),
            icon = Icons.Rounded.ReceiptLong,
            accent = ext.emerald,
            onClick = onOpenInvoices
        ),
        MoreMenuItem(
            tag = "more_reports",
            title = tr(isArabic, "التقارير المالية", "Financial Reports"),
            subtitle = tr(isArabic, "التحصيل اليومي/الشهري، الأرباح، والتصدير", "Collections, receivables, profit & CSV/PDF"),
            icon = Icons.Rounded.Assessment,
            accent = ext.gold,
            onClick = onOpenReports
        ),
        MoreMenuItem(
            tag = "more_reminders",
            title = tr(isArabic, "تذكيرات الدفع", "Payment Reminders"),
            subtitle = tr(isArabic, "جدولة التنبيهات ومشاركة واتساب", "Local notifications & WhatsApp sharing"),
            icon = Icons.Rounded.NotificationsActive,
            accent = ext.crimson,
            badge = if (pendingRemindersCount > 0) pendingRemindersCount.toString() else null,
            onClick = onOpenReminders
        ),
        MoreMenuItem(
            tag = "more_backup",
            title = tr(isArabic, "النسخ الاحتياطي والاستعادة", "Backup & Restore"),
            subtitle = tr(isArabic, "نسخ مشفّر AES-256 وتصدير قاعدة البيانات", "AES-256 encrypted backup & conflict-safe restore"),
            icon = Icons.Rounded.Backup,
            accent = ext.emerald,
            onClick = onOpenBackup
        ),
        MoreMenuItem(
            tag = "more_audit",
            title = tr(isArabic, "سجل التدقيق المالي", "Financial Audit Log"),
            subtitle = tr(isArabic, "تتبع جميع عمليات الإنشاء والتعديل والعكس", "Immutable history of ledger actions"),
            icon = Icons.Rounded.History,
            accent = ext.gold,
            onClick = onOpenAuditLog
        ),
        MoreMenuItem(
            tag = "more_settings",
            title = tr(isArabic, "الإعدادات ودليل APK", "Settings & APK Guide"),
            subtitle = tr(isArabic, "المتجر، العملات، اللغة، القفل، وبناء APK", "Profile, rates, RTL/LTR, PIN/Biometric, GitHub Actions"),
            icon = Icons.Rounded.Settings,
            accent = ext.mutedText,
            onClick = onOpenSettings
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                tr(isArabic, "المزيد والأدوات", "More & Merchant Tools"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(itemsList, key = { it.tag }) { entry ->
            GlassFinanceCard(
                onClick = entry.onClick,
                modifier = Modifier.testTag(entry.tag)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(entry.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.icon, contentDescription = entry.title, tint = entry.accent, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                    }
                    entry.badge?.let { badgeText ->
                        StatusBadge(
                            text = badgeText,
                            containerColor = entry.accent.copy(alpha = 0.18f),
                            contentColor = entry.accent
                        )
                    }
                }
            }
        }
    }
}
