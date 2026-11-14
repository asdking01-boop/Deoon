package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.ProductEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    defaultCurrency: CurrencyCode,
    isArabic: Boolean,
    onBack: () -> Unit,
    onSaveProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onImportCsv: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var search by remember { mutableStateOf("") }
    var lowStockOnly by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val importCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCsv(uri)
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExportCsv(uri)
    }

    val filtered = remember(products, search, lowStockOnly) {
        products.filter { p ->
            val q = search.isBlank() ||
                p.name.contains(search, ignoreCase = true) ||
                p.codeOrBarcode.contains(search, ignoreCase = true) ||
                p.category.contains(search, ignoreCase = true)
            val low = !lowStockOnly || p.availableQty <= p.lowStockThreshold
            q && low
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "المنتجات والمخزون", "Products & Inventory")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingProduct = null
                        showDialog = true
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = tr(isArabic, "إضافة منتج", "Add Product"))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr(isArabic, "ابحث بالاسم أو الباركود أو الفئة...", "Search name, barcode, category...")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = lowStockOnly,
                        onClick = { lowStockOnly = !lowStockOnly },
                        label = { Text(tr(isArabic, "تنبيه نقص المخزون", "Low Stock Only")) }
                    )
                    OutlinedButton(onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CSV")
                    }
                    OutlinedButton(onClick = { exportCsvLauncher.launch("products_catalog.csv") }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(isArabic, "تصدير", "Export"))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Inventory2,
                            title = tr(isArabic, "كتالوج المنتجات فارغ", "No Products Yet"),
                            subtitle = tr(
                                isArabic,
                                "أضف منتجاتك أو استوردها من ملف CSV لربطها بالفواتير وخصم المخزون تلقائياً.",
                                "Add products or import CSV to select items directly on debt invoices."
                            ),
                            actionLabel = tr(isArabic, "إضافة منتج", "Add Product"),
                            onAction = {
                                editingProduct = null
                                showDialog = true
                            }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { p ->
                        val lowStock = p.availableQty <= p.lowStockThreshold
                        GlassFinanceCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!p.imageUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = p.imageUri,
                                        contentDescription = p.name,
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(
                                            p.codeOrBarcode.takeIf { it.isNotBlank() },
                                            p.category.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ").ifBlank { p.currency.code },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.mutedText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            MoneyUtils.format(p.sellingPriceMinor, p.currency, isArabic),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ext.gold,
                                            fontWeight = FontWeight.Bold
                                        )
                                        StatusBadge(
                                            text = "${tr(isArabic, "المتاح:", "Qty:")} ${p.availableQty}",
                                            containerColor = if (lowStock) ext.crimsonContainer else ext.emeraldContainer,
                                            contentColor = if (lowStock) ext.crimson else ext.emerald
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    editingProduct = p
                                    showDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = tr(isArabic, "تعديل", "Edit"))
                                }
                                IconButton(onClick = { onDeleteProduct(p) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = tr(isArabic, "حذف", "Delete"), tint = ext.crimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ProductEditorDialog(
            initial = editingProduct,
            defaultCurrency = defaultCurrency,
            isArabic = isArabic,
            onDismiss = { showDialog = false },
            onSave = { prod ->
                onSaveProduct(prod)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductEntity?,
    defaultCurrency: CurrencyCode,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.codeOrBarcode ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var purchaseText by remember { mutableStateOf(initial?.let { MoneyUtils.formatEditable(it.purchasePriceMinor) } ?: "") }
    var sellingText by remember { mutableStateOf(initial?.let { MoneyUtils.formatEditable(it.sellingPriceMinor) } ?: "") }
    var qtyText by remember { mutableStateOf(initial?.availableQty?.toString() ?: "0") }
    var thresholdText by remember { mutableStateOf(initial?.lowStockThreshold?.toString() ?: "5") }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var imageUri by remember { mutableStateOf(initial?.imageUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) imageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr(isArabic, "إضافة منتج", "Add Product") else tr(isArabic, "تعديل منتج", "Edit Product")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr(isArabic, "اسم المنتج *", "Product Name *")) }, singleLine = true)
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text(tr(isArabic, "الرمز / الباركود", "Code / Barcode")) }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(tr(isArabic, "الفئة", "Category")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currency == CurrencyCode.ILS, onClick = { currency = CurrencyCode.ILS }, label = { Text("ILS ₪") })
                    FilterChip(selected = currency == CurrencyCode.USD, onClick = { currency = CurrencyCode.USD }, label = { Text("USD $") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchaseText,
                        onValueChange = { purchaseText = it },
                        label = { Text(tr(isArabic, "سعر الشراء", "Cost")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sellingText,
                        onValueChange = { sellingText = it },
                        label = { Text(tr(isArabic, "سعر البيع *", "Price *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text(tr(isArabic, "الكمية المتوفرة", "Quantity")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text(tr(isArabic, "حد التنبيه", "Low Alert")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUri == null) tr(isArabic, "صورة المنتج (اختياري)", "Product Image (Optional)") else tr(isArabic, "تم اختيار صورة ✓", "Image Selected ✓"))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sell = MoneyUtils.parseToMinorUnits(sellingText, allowZero = true) ?: 0L
                    val buy = MoneyUtils.parseToMinorUnits(purchaseText, allowZero = true) ?: 0L
                    val qty = qtyText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val thresh = thresholdText.toIntOrNull()?.coerceAtLeast(0) ?: 5
                    val now = System.currentTimeMillis()
                    onSave(
                        ProductEntity(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            codeOrBarcode = code.trim(),
                            purchasePriceMinor = buy,
                            sellingPriceMinor = sell,
                            currency = currency,
                            availableQty = qty,
                            lowStockThreshold = thresh,
                            category = category.trim(),
                            imageUri = imageUri,
                            createdAt = initial?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                },
                enabled = name.isNotBlank()
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
