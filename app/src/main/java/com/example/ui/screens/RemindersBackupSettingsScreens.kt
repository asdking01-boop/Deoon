package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.money.CurrencyCode
import com.example.data.export.PdfAndCsvExporter
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ReminderChannel
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import com.example.data.preferences.MerchantSettings
import com.example.data.preferences.ThemePreference
import com.example.domain.model.RestoreMode
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassFinanceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDateShort
import com.example.ui.components.formatDateTime
import com.example.ui.i18n.localized
import com.example.ui.i18n.tr
import com.example.ui.theme.LocalDebtBookColors
import com.example.ui.viewmodel.PendingRestoreState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminders: List<ReminderEntity>,
    customers: List<CustomerEntity>,
    isArabic: Boolean,
    onBack: () -> Unit,
    onCreateReminder: (customer: CustomerEntity, title: String, message: String, dueAt: Long, channel: ReminderChannel) -> Unit,
    onUpdateStatus: (String, ReminderStatus) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalDebtBookColors.current
    val customerMap = remember(customers) { customers.associateBy { it.id } }
    var showCreate by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "تذكيرات الدفع", "Payment Reminders")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        showCreate = true
                    }) {
                        Icon(Icons.Rounded.AddAlert, contentDescription = tr(isArabic, "تذكير جديد", "New Reminder"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (reminders.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Rounded.NotificationsActive,
                        title = tr(isArabic, "لا توجد تذكيرات مجدولة", "No Payment Reminders"),
                        subtitle = tr(
                            isArabic,
                            "أنشئ تذكيراً مخصصاً لإرسال تنبيه محلي أو مشاركة رسالة تذكير مهذبة عبر واتساب.",
                            "Create a reminder for local notification or explicit WhatsApp sharing."
                        ),
                        actionLabel = tr(isArabic, "إنشاء تذكير", "Create Reminder"),
                        onAction = { showCreate = true }
                    )
                }
            } else {
                items(reminders, key = { it.id }) { rem ->
                    val cust = customerMap[rem.customerId]
                    val isLate = rem.status == ReminderStatus.SCHEDULED && rem.dueAt < System.currentTimeMillis()
                    GlassFinanceCard(borderColor = if (isLate) ext.crimson else ext.cardBorder) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rem.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(cust?.fullName ?: "—", style = MaterialTheme.typography.bodySmall, color = ext.gold)
                                Text(rem.message, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                                Text("${tr(isArabic, "الموعد:", "Due:")} ${formatDateShort(rem.dueAt)}", style = MaterialTheme.typography.labelSmall)
                            }
                            StatusBadge(
                                text = if (isLate) tr(isArabic, "متأخر", "Overdue") else rem.status.localized(isArabic),
                                containerColor = if (isLate) ext.crimsonContainer else ext.goldContainer,
                                contentColor = if (isLate) ext.crimson else ext.gold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    PdfAndCsvExporter.shareTextViaWhatsApp(context, cust?.phone, rem.message)
                                    onUpdateStatus(rem.id, ReminderStatus.SENT)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = ext.emerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr(isArabic, "مشاركة واتساب", "Share WhatsApp"))
                            }
                            IconButton(onClick = { onDeleteReminder(rem) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = tr(isArabic, "حذف", "Delete"), tint = ext.crimson)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var selectedCust by remember { mutableStateOf(customers.firstOrNull { !it.isArchived }) }
        var expanded by remember { mutableStateOf(false) }
        var title by remember { mutableStateOf(tr(isArabic, "تذكير دفعة مستحقة", "Payment Due Reminder")) }
        var msg by remember {
            mutableStateOf(
                if (isArabic) "السلام عليكم، نود تذكيركم بموعد الدفعة المستحقة. شكراً لتعاونكم."
                else "Hello, friendly reminder regarding your upcoming payment due date."
            )
        }
        var dueAt by remember { mutableStateOf<Long?>(System.currentTimeMillis() + 86_400_000L) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(tr(isArabic, "تذكير جديد", "New Reminder")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (customers.isEmpty()) {
                        Text(tr(isArabic, "يرجى إضافة زبون أولاً.", "Add a customer first."))
                    } else {
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedCust?.fullName ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr(isArabic, "الزبون", "Customer")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                customers.filter { !it.isArchived }.forEach { c ->
                                    DropdownMenuItem(text = { Text(c.fullName) }, onClick = {
                                        selectedCust = c; expanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(tr(isArabic, "العنوان", "Title")) }, singleLine = true)
                        OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text(tr(isArabic, "نص التذكير", "Message")) }, maxLines = 3)
                        DatePickerField(tr(isArabic, "تاريخ التذكير", "Reminder Date"), dueAt, isArabic, { dueAt = it }, allowClear = false)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = selectedCust ?: return@Button
                        onCreateReminder(c, title, msg, dueAt ?: System.currentTimeMillis(), ReminderChannel.LOCAL_NOTIFICATION)
                        showCreate = false
                    },
                    enabled = selectedCust != null && title.isNotBlank() && msg.isNotBlank()
                ) {
                    Text(tr(isArabic, "حفظ", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(tr(isArabic, "إلغاء", "Cancel")) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    settings: MerchantSettings,
    pendingRestore: PendingRestoreState?,
    isArabic: Boolean,
    onBack: () -> Unit,
    onExportBackup: (Uri, passphrase: String?) -> Unit,
    onInspectRestore: (Uri, passphrase: String?, onNeedPassphrase: () -> Unit) -> Unit,
    onConfirmRestore: (RestoreMode) -> Unit,
    onDismissPendingRestore: () -> Unit,
    onSetAutoBackup: (Boolean) -> Unit
) {
    val ext = LocalDebtBookColors.current
    var encryptPassphrase by remember { mutableStateOf("") }
    var useEncryption by remember { mutableStateOf(true) }
    var pendingUriForDecrypt by remember { mutableStateOf<Uri?>(null) }
    var decryptPassphrase by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            onExportBackup(uri, if (useEncryption) encryptPassphrase.trim() else null)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onInspectRestore(uri, null) {
                pendingUriForDecrypt = uri
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "النسخ الاحتياطي والاستعادة", "Backup & Restore")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
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
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "حالة النسخ الاحتياطي المحلي", "Local Backup Status"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        settings.lastBackupAt?.let {
                            "${tr(isArabic, "آخر نسخة محفوظة:", "Last Backup:")} ${formatDateTime(it)}"
                        } ?: tr(isArabic, "لم يتم إنشاء نسخة احتياطية بعد.", "No backup created yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.gold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(isArabic, "تفعيل التذكير بالنسخ التلقائي", "Auto Backup Preference"))
                        Switch(checked = settings.autoBackupEnabled, onCheckedChange = onSetAutoBackup)
                    }
                }
            }

            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "تصدير قاعدة البيانات (مشفر AES-256 أو JSON)", "Export Database (AES-256 Encrypted or JSON)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(isArabic, "تشفير الملف بكلمة مرور (موصى به)", "Encrypt Backup with Passphrase"))
                        Switch(checked = useEncryption, onCheckedChange = { useEncryption = it })
                    }
                    if (useEncryption) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = encryptPassphrase,
                            onValueChange = { encryptPassphrase = it },
                            label = { Text(tr(isArabic, "كلمة مرور التشفير *", "Encryption Passphrase *")) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val extName = if (useEncryption) "smart_debt_book_backup.sdbk.json" else "smart_debt_book_backup.json"
                            exportLauncher.launch(extName)
                        },
                        enabled = !useEncryption || encryptPassphrase.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isArabic, "تصدير وحفظ النسخة الاحتياطية", "Export & Save Backup"))
                    }
                }
            }

            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "استعادة نسخة احتياطية", "Restore from Backup"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            isArabic,
                            "يفحص النظام تعارض التواريخ والبيانات الأحدث تلقائياً قبل الاستعادة ولا يستبدل أي بيانات بصمت.",
                            "Conflict detection compares backup timestamps against local records before restoring."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isArabic, "اختيار ملف نسخة احتياطية", "Select Backup File"))
                    }
                }
            }

            item {
                GlassFinanceCard(borderColor = ext.cardBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = ext.gold)
                        Column {
                            Text(tr(isArabic, "المزامنة السحابية الاختيارية", "Optional Cloud Sync"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                tr(
                                    isArabic,
                                    "غير مفعّلة حالياً: تتطلب تهيئة مشروع Firebase/Google Cloud واختبار المصادقة والتخزين قبل التشغيل. التطبيق يعمل حالياً بدون إنترنت بالكامل مع نسخ محلي مشفّر.",
                                    "Not configured: Cloud backup requires explicit backend authentication and storage setup. Offline encrypted local backups are active."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingUriForDecrypt != null) {
        AlertDialog(
            onDismissRequest = { pendingUriForDecrypt = null },
            title = { Text(tr(isArabic, "أدخل كلمة مرور فك التشفير", "Enter Backup Passphrase")) },
            text = {
                OutlinedTextField(
                    value = decryptPassphrase,
                    onValueChange = { decryptPassphrase = it },
                    label = { Text(tr(isArabic, "كلمة المرور", "Passphrase")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingUriForDecrypt ?: return@Button
                    pendingUriForDecrypt = null
                    onInspectRestore(uri, decryptPassphrase) {}
                }) {
                    Text(tr(isArabic, "فك التشفير والفحص", "Decrypt & Inspect"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUriForDecrypt = null }) { Text(tr(isArabic, "إلغاء", "Cancel")) }
            }
        )
    }

    pendingRestore?.let { pending ->
        val c = pending.conflict
        AlertDialog(
            onDismissRequest = onDismissPendingRestore,
            title = { Text(tr(isArabic, "تقرير فحص الاستعادة والتعارض", "Restore Conflict Inspection")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${tr(isArabic, "تاريخ النسخة:", "Backup Date:")} ${formatDateTime(c.backupCreatedAt)}")
                    Text("${tr(isArabic, "في النسخة:", "Backup:")} ${c.backupCustomersCount} ${tr(isArabic, "زبائن", "cust")}, ${c.backupDebtsCount} ${tr(isArabic, "ديون", "debts")}, ${c.backupPaymentsCount} ${tr(isArabic, "دفعات", "pay")}")
                    Text("${tr(isArabic, "على الجهاز:", "Local:")} ${c.localCustomersCount} ${tr(isArabic, "زبائن", "cust")}, ${c.localDebtsCount} ${tr(isArabic, "ديون", "debts")}, ${c.localPaymentsCount} ${tr(isArabic, "دفعات", "pay")}")
                    if (c.hasNewerLocalData) {
                        Text(
                            tr(
                                isArabic,
                                "تنبيه: توجد بيانات محلية أحدث من تاريخ هذه النسخة! يُنصح بوضع الدمج للحفاظ على البيانات الأحدث.",
                                "Warning: Local device has newer data than this backup! Merge mode preserves newer records."
                            ),
                            color = ext.crimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onConfirmRestore(RestoreMode.MERGE_KEEP_NEWER) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(isArabic, "دمج والاحتفاظ بالأحدث", "Merge (Keep Newer)"))
                    }
                    OutlinedButton(
                        onClick = { onConfirmRestore(RestoreMode.REPLACE_ALL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(isArabic, "استبدال كامل للبيانات", "Replace All Local Data"), color = ext.crimson)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPendingRestore) { Text(tr(isArabic, "إلغاء", "Cancel")) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: MerchantSettings,
    onBack: () -> Unit,
    onSaveProfile: (name: String, phone: String, logoUri: String?) -> Unit,
    onSetDefaultCurrency: (CurrencyCode) -> Unit,
    onSetManualRate: (String) -> Unit,
    onRefreshRateOnline: () -> Unit,
    onSetLanguageArabic: (Boolean) -> Unit,
    onSetThemePreference: (ThemePreference) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onEnablePinLock: (pin: String, biometric: Boolean) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onDisableAppLock: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val isArabic = settings.isArabic
    val ext = LocalDebtBookColors.current

    var mName by remember(settings.merchantName) { mutableStateOf(settings.merchantName) }
    var mPhone by remember(settings.merchantPhone) { mutableStateOf(settings.merchantPhone) }
    var mLogo by remember(settings.merchantLogoUri) { mutableStateOf(settings.merchantLogoUri) }
    var rateText by remember(settings.usdToIlsRate) { mutableStateOf(settings.usdToIlsRate) }
    var showPinDialog by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mLogo = uri.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "الإعدادات", "Settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
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
            // Merchant Profile
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "بيانات المتجر والفواتير", "Merchant Profile & Branding"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!mLogo.isNullOrBlank()) {
                            AsyncImage(
                                model = mLogo,
                                contentDescription = "Logo",
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Rounded.Storefront, contentDescription = null, tint = ext.gold, modifier = Modifier.size(44.dp))
                        }
                        OutlinedButton(onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(tr(isArabic, "اختيار شعار المتجر", "Choose Business Logo"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mName, onValueChange = { mName = it }, label = { Text(tr(isArabic, "اسم المتجر", "Business Name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mPhone, onValueChange = { mPhone = it }, label = { Text(tr(isArabic, "هاتف المتجر", "Business Phone")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { onSaveProfile(mName, mPhone, mLogo) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(isArabic, "حفظ بيانات المتجر", "Save Business Profile"))
                    }
                }
            }

            // Currency & Exchange Rate
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "العملة وسعر الصرف", "Currency & Exchange Rate"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(tr(isArabic, "العملة الافتراضية:", "Default Currency:"), style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.defaultCurrency == CurrencyCode.ILS, onClick = { onSetDefaultCurrency(CurrencyCode.ILS) }, label = { Text(CurrencyCode.ILS.label(isArabic)) })
                        FilterChip(selected = settings.defaultCurrency == CurrencyCode.USD, onClick = { onSetDefaultCurrency(CurrencyCode.USD) }, label = { Text(CurrencyCode.USD.label(isArabic)) })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text(tr(isArabic, "سعر صرف 1 دولار بالشيكل (1 USD = ? ILS)", "Exchange Rate (1 USD = ? ILS)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSetManualRate(rateText) }, modifier = Modifier.weight(1f)) {
                            Text(tr(isArabic, "حفظ يدوياً", "Save Manual"))
                        }
                        OutlinedButton(onClick = onRefreshRateOnline, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr(isArabic, "تحديث تلقائي", "Auto Sync"))
                        }
                    }
                    if (settings.lastRateUpdatedAt > 0L) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${tr(isArabic, "آخر تحديث:", "Last updated:")} ${formatDateTime(settings.lastRateUpdatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.mutedText
                        )
                    }
                }
            }

            // Language & Theme
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "اللغة والمظهر", "Language & Appearance"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.isArabic, onClick = { onSetLanguageArabic(true) }, label = { Text("العربية (RTL)") }, modifier = Modifier.testTag("lang_ar_chip"))
                        FilterChip(selected = !settings.isArabic, onClick = { onSetLanguageArabic(false) }, label = { Text("English (LTR)") }, modifier = Modifier.testTag("lang_en_chip"))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.themePreference == ThemePreference.DARK, onClick = { onSetThemePreference(ThemePreference.DARK) }, label = { Text(tr(isArabic, "داكن", "Dark")) })
                        FilterChip(selected = settings.themePreference == ThemePreference.LIGHT, onClick = { onSetThemePreference(ThemePreference.LIGHT) }, label = { Text(tr(isArabic, "فاتح", "Light")) })
                        FilterChip(selected = settings.themePreference == ThemePreference.SYSTEM, onClick = { onSetThemePreference(ThemePreference.SYSTEM) }, label = { Text(tr(isArabic, "النظام", "System")) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(isArabic, "تنبيهات مواعيد الدفع المحلية", "Local Payment Notifications"))
                        Switch(checked = settings.notificationsEnabled, onCheckedChange = onSetNotificationsEnabled)
                    }
                }
            }

            // Security / App Lock
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "الأمان وقفل التطبيق", "Security & App Lock"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(isArabic, "قفل برمز PIN", "PIN App Lock"))
                        Switch(
                            checked = settings.appLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable) showPinDialog = true else onDisableAppLock()
                            }
                        )
                    }
                    if (settings.appLockEnabled) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(isArabic, "الفتح بالبصمة (Biometric)", "Biometric Unlock"))
                            Switch(checked = settings.biometricEnabled, onCheckedChange = onSetBiometricEnabled)
                        }
                        TextButton(onClick = { showPinDialog = true }) {
                            Text(tr(isArabic, "تغيير رمز PIN", "Change PIN"))
                        }
                    }
                    OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(isArabic, "إدارة النسخ الاحتياطي وتصدير البيانات", "Manage Backup & Export"))
                    }
                }
            }

            // About & GitHub Actions Debug APK Instructions
            item {
                GlassFinanceCard {
                    Text(tr(isArabic, "حول التطبيق وبناء ملف Debug APK عبر GitHub Actions", "About & GitHub Actions Debug APK Guide"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isArabic) {
                            "دفتر التاجر (Smart Debt Book) v1.0.0\n" +
                                "نظام محاسبي محلي لتجار فلسطين يدعم الشيكل (₪) والدولار ($)، الفواتير العربية RTL، والمخزون.\n\n" +
                                "لبناء ملف APK تجريبي تلقائياً عبر GitHub Actions:\n" +
                                "1. ادفع المشروع إلى مستودع GitHub.\n" +
                                "2. أنشئ ملف .github/workflows/android-debug.yml.\n" +
                                "3. استخدم actions/checkout@v4 و actions/setup-java@v4 (JDK 17).\n" +
                                "4. شغّل gradle assembleDebug (أو ./gradlew assembleDebug).\n" +
                                "5. ارفع المسار app/build/outputs/apk/debug/app-debug.apk عبر actions/upload-artifact@v4."
                        } else {
                            "Smart Debt Book v1.0.0\n" +
                                "Offline-first merchant ledger supporting ILS (₪) and USD ($), Arabic RTL PDFs, and inventory.\n\n" +
                                "Building a Debug APK via GitHub Actions:\n" +
                                "1. Push the Android project to a GitHub repository.\n" +
                                "2. Add .github/workflows/android-debug.yml.\n" +
                                "3. Use actions/checkout@v4 and actions/setup-java@v4 (Temurin JDK 17).\n" +
                                "4. Run ./gradlew assembleDebug.\n" +
                                "5. Upload app/build/outputs/apk/debug/app-debug.apk via actions/upload-artifact@v4."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var bioChecked by remember { mutableStateOf(settings.biometricEnabled) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(tr(isArabic, "تعيين رمز قفل التطبيق (PIN)", "Set App Lock PIN")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text(tr(isArabic, "رمز من 4-8 أرقام", "4-8 digit PIN")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(isArabic, "السماح بالبصمة", "Enable Biometric"))
                        Switch(checked = bioChecked, onCheckedChange = { bioChecked = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEnablePinLock(pinInput, bioChecked)
                        showPinDialog = false
                    },
                    enabled = pinInput.length >= 4
                ) {
                    Text(tr(isArabic, "تفعيل القفل", "Enable Lock"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(tr(isArabic, "إلغاء", "Cancel")) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    logs: List<AuditLogEntity>,
    isArabic: Boolean,
    onBack: () -> Unit
) {
    val ext = LocalDebtBookColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(isArabic, "سجل التدقيق المالي", "Financial Audit Log")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(isArabic, "رجوع", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                GlassFinanceCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${log.entityType} • ${log.action}", fontWeight = FontWeight.Bold, color = ext.gold)
                        Text(formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(
    isArabic: Boolean,
    biometricEnabled: Boolean,
    onSubmitPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit
) {
    val ext = LocalDebtBookColors.current
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = ext.gold, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            tr(isArabic, "دفتر التاجر مقفل", "Smart Debt Book Locked"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text(tr(isArabic, "أدخل رمز PIN", "Enter PIN")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSubmitPin(pin) },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(tr(isArabic, "فتح التطبيق", "Unlock"))
        }
        if (biometricEnabled) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onTriggerBiometric, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(isArabic, "استخدام البصمة", "Use Biometric"))
            }
        }
    }
}
