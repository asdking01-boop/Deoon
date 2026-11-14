package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.domain.model.MonthlyStatPoint
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlassFinanceCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalDebtBookColors.current.cardBorder,
    containerColor: Color = LocalDebtBookColors.current.glassSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = modifier
        .clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Card(
        modifier = baseModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun CurrencyLedgerCard(
    currency: CurrencyCode,
    outstandingMinor: Long,
    collectedMinor: Long,
    overdueMinor: Long,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    val gradient = if (ext.isDark) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.White, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.45f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ext.goldContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currency.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = ext.gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = if (isArabic) "محفظة ${currency.nameAr}" else "${currency.nameEn} Ledger",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currency.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.mutedText
                        )
                    }
                }
                if (overdueMinor > 0L) {
                    StatusBadge(
                        text = (if (isArabic) "متأخر: " else "Overdue: ") +
                            MoneyUtils.format(overdueMinor, currency, isArabic),
                        containerColor = ext.crimsonContainer,
                        contentColor = ext.crimson
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = tr(isArabic, "الديون القائمة", "Outstanding"),
                    value = MoneyUtils.format(outstandingMinor, currency, isArabic),
                    accent = ext.crimson,
                    container = ext.crimsonContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = tr(isArabic, "الدفعات المحصّلة", "Collected"),
                    value = MoneyUtils.format(collectedMinor, currency, isArabic),
                    accent = ext.emerald,
                    container = ext.emeraldContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalDebtBookColors.current.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CustomerAvatar(
    name: String,
    photoUri: String?,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ext.goldContainer)
            .border(1.dp, ext.gold.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = ext.gold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MonthlyFinanceChart(
    stats: List<MonthlyStatPoint>,
    currency: CurrencyCode,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    var selectedIdx by remember(stats, currency) { mutableIntStateOf((stats.size - 1).coerceAtLeast(0)) }

    GlassFinanceCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(
                    isArabic,
                    "إحصائيات التحصيل والديون (${currency.symbol})",
                    "Monthly Collections & Debts (${currency.code})"
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = ext.crimson, label = tr(isArabic, "ديون", "Debts"))
                LegendDot(color = ext.emerald, label = tr(isArabic, "تحصيل", "Collected"))
            }
        }

        Spacer(Modifier.height(10.dp))

        val selected = stats.getOrNull(selectedIdx)
        if (selected != null) {
            val dVal = if (currency == CurrencyCode.ILS) selected.debtsIlsMinor else selected.debtsUsdMinor
            val cVal = if (currency == CurrencyCode.ILS) selected.collectedIlsMinor else selected.collectedUsdMinor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ext.subtleSurface.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selected.monthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.gold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${tr(isArabic, "ديون:", "Debts:")} ${MoneyUtils.format(dVal, currency, isArabic)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.crimson
                )
                Text(
                    text = "${tr(isArabic, "تحصيل:", "Paid:")} ${MoneyUtils.format(cVal, currency, isArabic)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.emerald
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (stats.isEmpty()) {
            Text(
                text = tr(isArabic, "لا توجد بيانات شهرية بعد", "No monthly data yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = ext.mutedText
            )
        } else {
            val maxVal = stats.maxOf {
                val d = if (currency == CurrencyCode.ILS) it.debtsIlsMinor else it.debtsUsdMinor
                val c = if (currency == CurrencyCode.ILS) it.collectedIlsMinor else it.collectedUsdMinor
                maxOf(d, c)
            }.coerceAtLeast(100L)

            val crimsonColor = ext.crimson
            val emeraldColor = ext.emerald
            val gridColor = ext.cardBorder.copy(alpha = 0.5f)
            val goldColor = ext.gold

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .pointerInput(stats) {
                        detectTapGestures { offset ->
                            if (stats.isNotEmpty()) {
                                val slot = (offset.x / (size.width / stats.size.toFloat())).toInt()
                                    .coerceIn(0, stats.lastIndex)
                                selectedIdx = slot
                            }
                        }
                    }
            ) {
                val count = stats.size
                val slotWidth = size.width / count.toFloat()
                val chartHeight = size.height - 12f
                // Grid lines
                for (g in 0..3) {
                    val gy = chartHeight * (g / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(size.width, gy),
                        strokeWidth = 1f
                    )
                }

                stats.forEachIndexed { idx, pt ->
                    val dVal = if (currency == CurrencyCode.ILS) pt.debtsIlsMinor else pt.debtsUsdMinor
                    val cVal = if (currency == CurrencyCode.ILS) pt.collectedIlsMinor else pt.collectedUsdMinor
                    val dHeight = ((dVal.toFloat() / maxVal.toFloat()) * (chartHeight - 10f)).coerceAtLeast(if (dVal > 0L) 6f else 2f)
                    val cHeight = ((cVal.toFloat() / maxVal.toFloat()) * (chartHeight - 10f)).coerceAtLeast(if (cVal > 0L) 6f else 2f)

                    val barWidth = (slotWidth * 0.28f).coerceAtMost(24f)
                    val centerX = idx * slotWidth + slotWidth / 2f

                    if (idx == selectedIdx) {
                        drawRoundRect(
                            color = goldColor.copy(alpha = 0.14f),
                            topLeft = Offset(idx * slotWidth + 4f, 0f),
                            size = Size(slotWidth - 8f, chartHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }

                    drawRoundRect(
                        color = crimsonColor,
                        topLeft = Offset(centerX - barWidth - 3f, chartHeight - dHeight),
                        size = Size(barWidth, dHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = emeraldColor,
                        topLeft = Offset(centerX + 3f, chartHeight - cHeight),
                        size = Size(barWidth, cHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                stats.forEachIndexed { idx, pt ->
                    Text(
                        text = pt.monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idx == selectedIdx) ext.gold else ext.mutedText,
                        fontWeight = if (idx == selectedIdx) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { selectedIdx = idx }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = LocalDebtBookColors.current.mutedText)
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalDebtBookColors.current
    GlassFinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ext.goldContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ext.gold, modifier = Modifier.size(28.dp))
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ext.mutedText
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, ext.gold),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.gold)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    selectedMillis: Long?,
    isArabic: Boolean,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val formatted = selectedMillis?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
    } ?: tr(isArabic, "غير محدد", "Not set")

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: $formatted")
    }

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(state.selectedDateMillis)
                    showDialog = false
                }) {
                    Text(tr(isArabic, "تأكيد", "OK"))
                }
            },
            dismissButton = {
                Row {
                    if (allowClear && selectedMillis != null) {
                        TextButton(onClick = {
                            onDateSelected(null)
                            showDialog = false
                        }) {
                            Text(tr(isArabic, "مسح", "Clear"))
                        }
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text(tr(isArabic, "إلغاء", "Cancel"))
                    }
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

fun formatDateShort(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
