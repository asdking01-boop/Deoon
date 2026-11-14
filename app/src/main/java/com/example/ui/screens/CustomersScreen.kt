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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.export.PdfAndCsvExporter
import com.example.data.local.entity.CustomerEntity
import com.example.domain.model.CustomerFinancialSummary
import com.example.ui.components.CustomerAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors

enum class CustomerFilter { ALL, WITH_DEBT, OVERDUE, ARCHIVED }
enum class CustomerSort { LATEST_ACTIVITY, HIGHEST_DEBT, NAME }

@Composable
fun CustomersScreen(
    summaries: List<CustomerFinancialSummary>,
    isArabic: Boolean,
    onOpenCustomer: (String) -> Unit,
    onSaveCustomer: (id: String?, name: String, phone: String, address: String, photoUri: String?, notes: String) -> Unit,
    showAddDialogInitially: Boolean = false,
    onAddDialogConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = LocalDebtBookColors.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CustomerFilter.ALL) }
    var sort by remember { mutableStateOf(CustomerSort.LATEST_ACTIVITY) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerDialog by remember(showAddDialogInitially) {
        mutableStateOf(showAddDialogInitially).also {
            if (showAddDialogInitially) onAddDialogConsumed()
        }
    }

    val filtered = remember(summaries, searchQuery, filter, sort) {
        summaries.filter { s ->
            val matchesQuery = searchQuery.isBlank() ||
                s.customer.fullName.contains(searchQuery, ignoreCase = true) ||
                s.customer.phone.contains(searchQuery, ignoreCase = true) ||
                s.customer.address.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filter) {
                CustomerFilter.ALL -> !s.customer.isArchived
                CustomerFilter.WITH_DEBT -> !s.customer.isArchived && s.hasAnyDebt
                CustomerFilter.OVERDUE -> !s.customer.isArchived && s.overdueDebtsCount > 0
                CustomerFilter.ARCHIVED -> s.customer.isArchived
            }
            matchesQuery && matchesFilter
        }.let { list ->
            when (sort) {
                CustomerSort.LATEST_ACTIVITY -> list.sortedByDescending { it.latestActivityAt }
                CustomerSort.HIGHEST_DEBT -> list.sortedByDescending { it.outstandingIlsMinor + (it.outstandingUsdMinor * 360L / 100L) }
                CustomerSort.NAME -> list.sortedBy { it.customer.fullName }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(isArabic, "الزبائن (${filtered.size})", "Customers (${filtered.size})"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        editingCustomer = null
                        showCustomerDialog = true
                    },
                    modifier = Modifier.testTag("add_customer_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(isArabic, "زبون جديد", "Add"))
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("customer_search_input"),
                placeholder = { Text(tr(isArabic, "ابحث بالاسم أو الهاتف أو العنوان...", "Search name, phone, address...")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == CustomerFilter.ALL,
                    onClick = { filter = CustomerFilter.ALL },
                    label = { Text(tr(isArabic, "النشطون", "Active")) }
                )
                FilterChip(
                    selected = filter == CustomerFilter.WITH_DEBT,
                    onClick = { filter = CustomerFilter.WITH_DEBT },
                    label = { Text(tr(isArabic, "عليهم ديون", "With Debt")) }
                )
                FilterChip(
                    selected = filter == CustomerFilter.OVERDUE,
                    onClick = { filter = CustomerFilter.OVERDUE },
                    label = { Text(tr(isArabic, "متأخرون", "Overdue")) }
                )
                FilterChip(
                    selected = filter == CustomerFilter.ARCHIVED,
                    onClick = { filter = CustomerFilter.ARCHIVED },
                    label = { Text(tr(isArabic, "الأرشيف", "Archived")) }
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr(isArabic, "ترتيب:", "Sort:"),
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.mutedText
                )
                FilterChip(
                    selected = sort == CustomerSort.LATEST_ACTIVITY,
                    onClick = { sort = CustomerSort.LATEST_ACTIVITY },
                    label = { Text(tr(isArabic, "أحدث نشاط", "Latest")) }
                )
                FilterChip(
                    selected = sort == CustomerSort.HIGHEST_DEBT,
                    onClick = { sort = CustomerSort.HIGHEST_DEBT },
                    label = { Text(tr(isArabic, "أعلى دين", "Highest Debt")) }
                )
                FilterChip(
                    selected = sort == CustomerSort.NAME,
                    onClick = { sort = CustomerSort.NAME },
                    label = { Text(tr(isArabic, "الاسم", "Name")) }
                )
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
                        icon = Icons.Rounded.Group,
                        title = tr(isArabic, "لا يوجد زبائن مطابقون", "No Matching Customers"),
                        subtitle = tr(
                            isArabic,
                            "أضف زبوناً جديداً لفتح حساب مالي مستقل له بالشيكل والدولار.",
                            "Add a customer to create their dedicated ILS and USD financial ledger."
                        ),
                        actionLabel = tr(isArabic, "إضافة زبون", "Add Customer"),
                        onAction = {
                            editingCustomer = null
                            showCustomerDialog = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.customer.id }) { summary ->
                    val c = summary.customer
                    GlassFinanceCard(
                        onClick = { onOpenCustomer(c.id) },
                        modifier = Modifier.testTag("customer_card_${c.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CustomerAvatar(name = c.fullName, photoUri = c.photoUri)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = c.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (summary.overdueDebtsCount > 0) {
                                        StatusBadge(
                                            text = tr(isArabic, "متأخر", "Overdue"),
                                            containerColor = ext.crimsonContainer,
                                            contentColor = ext.crimson
                                        )
                                    }
                                }
                                if (c.phone.isNotBlank()) {
                                    Text(c.phone, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (summary.outstandingIlsMinor > 0L) {
                                        StatusBadge(
                                            text = MoneyUtils.format(summary.outstandingIlsMinor, CurrencyCode.ILS, isArabic),
                                            containerColor = ext.crimsonContainer,
                                            contentColor = ext.crimson
                                        )
                                    }
                                    if (summary.outstandingUsdMinor > 0L) {
                                        StatusBadge(
                                            text = MoneyUtils.format(summary.outstandingUsdMinor, CurrencyCode.USD, isArabic),
                                            containerColor = ext.crimsonContainer,
                                            contentColor = ext.crimson
                                        )
                                    }
                                    if (!summary.hasAnyDebt) {
                                        StatusBadge(
                                            text = tr(isArabic, "خالص الرصيد", "Settled"),
                                            containerColor = ext.emeraldContainer,
                                            contentColor = ext.emerald
                                        )
                                    }
                                }
                            }
                            if (c.phone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val msg = if (isArabic) {
                                            "مرحباً ${c.fullName}، نود متابعة حسابكم في دفتر التاجر."
                                        } else {
                                            "Hello ${c.fullName}, reaching out regarding your merchant account."
                                        }
                                        PdfAndCsvExporter.shareTextViaWhatsApp(context, c.phone, msg)
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Chat,
                                        contentDescription = "WhatsApp",
                                        tint = ext.emerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomerDialog) {
        CustomerEditorDialog(
            initial = editingCustomer,
            isArabic = isArabic,
            onDismiss = { showCustomerDialog = false },
            onSave = { name, phone, address, photo, notes ->
                onSaveCustomer(editingCustomer?.id, name, phone, address, photo, notes)
                showCustomerDialog = false
            }
        )
    }
}

@Composable
fun CustomerEditorDialog(
    initial: CustomerEntity?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, photoUri: String?, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.fullName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var photoUri by remember { mutableStateOf(initial?.photoUri) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) tr(isArabic, "إضافة زبون جديد", "Add Customer")
                else tr(isArabic, "تعديل بيانات الزبون", "Edit Customer")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CustomerAvatar(name = name.ifBlank { "ز" }, photoUri = photoUri, size = 52.dp)
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(isArabic, "صورة اختيارية", "Optional Photo"))
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr(isArabic, "الاسم الكامل *", "Full Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(tr(isArabic, "رقم الهاتف / واتساب", "Phone / WhatsApp")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(tr(isArabic, "العنوان", "Address")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(tr(isArabic, "ملاحظات", "Notes")) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, address, photoUri, notes) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(tr(isArabic, "حفظ", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(isArabic, "إلغاء", "Cancel"))
            }
        }
    )
}
