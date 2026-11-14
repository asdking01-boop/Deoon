package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.entity.ReminderStatus
import com.example.data.preferences.ThemePreference
import com.example.ui.i18n.tr
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtDetailScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.PaymentDetailScreen
import com.example.ui.screens.PaymentsScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LocalDebtBookColors
import com.example.ui.theme.SmartDebtBookTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiFeedback

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            SmartDebtBookRoot(
                vm = vm,
                onRequestBiometric = { isArabic, onAuthed ->
                    launchBiometricPrompt(isArabic, onAuthed)
                }
            )
        }
    }

    private fun launchBiometricPrompt(isArabic: Boolean, onAuthed: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthed()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isArabic) "فتح دفتر التاجر" else "Unlock Smart Debt Book")
            .setSubtitle(if (isArabic) "استخدم البصمة للدخول إلى السجل المالي" else "Authenticate to access your financial ledger")
            .setNegativeButtonText(if (isArabic) "إلغاء" else "Cancel")
            .build()
        prompt.authenticate(info)
    }
}

private data class TopTabItem(
    val route: AppRoute,
    val labelAr: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun SmartDebtBookRoot(
    vm: MainViewModel,
    onRequestBiometric: (isArabic: Boolean, onAuthed: () -> Unit) -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isUnlocked by vm.isUnlocked.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val dashboardSummary by vm.dashboardSummary.collectAsStateWithLifecycle()
    val customerSummaries by vm.customerSummaries.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val allLineItems by vm.allLineItems.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val auditLogs by vm.auditLogs.collectAsStateWithLifecycle()
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()

    val isDark = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val isArabic = settings.isArabic

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.feedback.collect { event ->
            when (event) {
                is UiFeedback.Message -> {
                    snackbarHostState.showSnackbar(if (settings.isArabic) event.ar else event.en)
                }
            }
        }
    }

    SmartDebtBookTheme(darkTheme = isDark, isArabic = isArabic) {
        if (!isUnlocked && settings.appLockEnabled) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppLockScreen(
                    isArabic = isArabic,
                    biometricEnabled = settings.biometricEnabled,
                    onSubmitPin = { vm.tryUnlockWithPin(it) },
                    onTriggerBiometric = { onRequestBiometric(isArabic) { vm.markUnlockedBiometric() } }
                )
            }
            return@SmartDebtBookTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        var openCustomerDialogOnTab by remember { mutableStateOf(false) }
        var openDebtDialogOnTab by remember { mutableStateOf(false) }
        var openPaymentDialogOnTab by remember { mutableStateOf(false) }
        var preselectedCustomerId by remember { mutableStateOf<String?>(null) }
        var preselectedDebtId by remember { mutableStateOf<String?>(null) }
        var showQuickTxMenu by remember { mutableStateOf(false) }

        val topTabs = remember {
            listOf(
                TopTabItem(AppRoute.Dashboard, "الرئيسية", "Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "tab_dashboard"),
                TopTabItem(AppRoute.Customers, "الزبائن", "Customers", Icons.Rounded.Group, Icons.Outlined.Group, "tab_customers"),
                TopTabItem(AppRoute.Debts, "الديون", "Debts", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong, "tab_debts"),
                TopTabItem(AppRoute.Payments, "الدفعات", "Payments", Icons.Rounded.Payments, Icons.Outlined.Payments, "tab_payments"),
                TopTabItem(AppRoute.More, "المزيد", "More", Icons.Rounded.MoreHoriz, Icons.Outlined.MoreHoriz, "tab_more")
            )
        }

        val isTopLevel = currentDestination?.let { dest ->
            dest.hasRoute<AppRoute.Dashboard>() ||
                dest.hasRoute<AppRoute.Customers>() ||
                dest.hasRoute<AppRoute.Debts>() ||
                dest.hasRoute<AppRoute.Payments>() ||
                dest.hasRoute<AppRoute.More>()
        } ?: true

        fun navigateTopTab(route: AppRoute) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (isTopLevel && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topTabs.forEach { item ->
                                val selected = when (item.route) {
                                    AppRoute.Dashboard -> currentDestination?.hasRoute<AppRoute.Dashboard>() == true
                                    AppRoute.Customers -> currentDestination?.hasRoute<AppRoute.Customers>() == true
                                    AppRoute.Debts -> currentDestination?.hasRoute<AppRoute.Debts>() == true
                                    AppRoute.Payments -> currentDestination?.hasRoute<AppRoute.Payments>() == true
                                    AppRoute.More -> currentDestination?.hasRoute<AppRoute.More>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = if (isArabic) item.labelAr else item.labelEn
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = if (isArabic) item.labelAr else item.labelEn,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (isTopLevel) {
                        FloatingActionButton(
                            onClick = { showQuickTxMenu = true },
                            containerColor = LocalDebtBookColors.current.gold,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_quick_tx")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = tr(isArabic, "معاملة جديدة", "New Transaction"))
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isTopLevel && useNavRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            topTabs.forEach { item ->
                                val selected = when (item.route) {
                                    AppRoute.Dashboard -> currentDestination?.hasRoute<AppRoute.Dashboard>() == true
                                    AppRoute.Customers -> currentDestination?.hasRoute<AppRoute.Customers>() == true
                                    AppRoute.Debts -> currentDestination?.hasRoute<AppRoute.Debts>() == true
                                    AppRoute.Payments -> currentDestination?.hasRoute<AppRoute.Payments>() == true
                                    AppRoute.More -> currentDestination?.hasRoute<AppRoute.More>() == true
                                    else -> false
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(item.route) },
                                    icon = {
                                        Icon(
                                            if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = if (isArabic) item.labelAr else item.labelEn
                                        )
                                    },
                                    label = { Text(if (isArabic) item.labelAr else item.labelEn) }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Dashboard,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 880.dp)
                        ) {
                            composable<AppRoute.Dashboard> {
                                DashboardScreen(
                                    settings = settings,
                                    summary = dashboardSummary,
                                    customers = customers,
                                    debts = debts,
                                    payments = payments,
                                    reminders = reminders,
                                    onAddCustomer = {
                                        openCustomerDialogOnTab = true
                                        navigateTopTab(AppRoute.Customers)
                                    },
                                    onAddDebt = {
                                        preselectedCustomerId = null
                                        openDebtDialogOnTab = true
                                        navigateTopTab(AppRoute.Debts)
                                    },
                                    onAddPayment = {
                                        preselectedCustomerId = null
                                        preselectedDebtId = null
                                        openPaymentDialogOnTab = true
                                        navigateTopTab(AppRoute.Payments)
                                    },
                                    onOpenDebt = { id -> navController.navigate(AppRoute.DebtDetail(id)) },
                                    onOpenPayment = { id -> navController.navigate(AppRoute.PaymentDetail(id)) },
                                    onOpenReminders = { navController.navigate(AppRoute.Reminders) }
                                )
                            }

                            composable<AppRoute.Customers> {
                                CustomersScreen(
                                    summaries = customerSummaries,
                                    isArabic = isArabic,
                                    onOpenCustomer = { id -> navController.navigate(AppRoute.CustomerDetail(id)) },
                                    onSaveCustomer = { id, name, phone, address, photo, notes ->
                                        vm.saveCustomer(id, name, phone, address, photo, notes)
                                    },
                                    showAddDialogInitially = openCustomerDialogOnTab,
                                    onAddDialogConsumed = { openCustomerDialogOnTab = false }
                                )
                            }

                            composable<AppRoute.CustomerDetail> { entry ->
                                val route = entry.toRoute<AppRoute.CustomerDetail>()
                                val summary = customerSummaries.firstOrNull { it.customer.id == route.customerId }
                                val custDebts = debts.filter { it.customerId == route.customerId }
                                val custPays = payments.filter { it.customerId == route.customerId }
                                CustomerDetailScreen(
                                    summary = summary,
                                    debts = custDebts,
                                    payments = custPays,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onEditCustomer = { name, phone, address, photo, notes ->
                                        vm.saveCustomer(route.customerId, name, phone, address, photo, notes)
                                    },
                                    onToggleArchive = { arch -> vm.setCustomerArchived(route.customerId, arch) },
                                    onDeleteCustomer = {
                                        vm.deleteCustomer(route.customerId) { navController.popBackStack() }
                                    },
                                    onAddDebtForCustomer = {
                                        preselectedCustomerId = route.customerId
                                        openDebtDialogOnTab = true
                                        navigateTopTab(AppRoute.Debts)
                                    },
                                    onAddPaymentForCustomer = {
                                        preselectedCustomerId = route.customerId
                                        preselectedDebtId = null
                                        openPaymentDialogOnTab = true
                                        navigateTopTab(AppRoute.Payments)
                                    },
                                    onOpenDebt = { id -> navController.navigate(AppRoute.DebtDetail(id)) },
                                    onOpenPayment = { id -> navController.navigate(AppRoute.PaymentDetail(id)) },
                                    onExportStatementPdf = { shareWa ->
                                        summary?.let { vm.exportCustomerStatementPdf(it, shareWa, false) }
                                    }
                                )
                            }

                            composable<AppRoute.Debts> {
                                DebtsScreen(
                                    debts = debts,
                                    customers = customers,
                                    products = products,
                                    defaultCurrency = settings.defaultCurrency,
                                    isArabic = isArabic,
                                    onOpenDebt = { id -> navController.navigate(AppRoute.DebtDetail(id)) },
                                    onCreateDebt = { cId, curr, amt, items, disc, tax, due, notes, img ->
                                        vm.createDebt(cId, curr, amt, items, disc, tax, due, notes, img)
                                    },
                                    preselectedCustomerId = preselectedCustomerId,
                                    showAddDialogInitially = openDebtDialogOnTab,
                                    onAddDialogConsumed = { openDebtDialogOnTab = false }
                                )
                            }

                            composable<AppRoute.DebtDetail> { entry ->
                                val route = entry.toRoute<AppRoute.DebtDetail>()
                                val debt = debts.firstOrNull { it.id == route.debtId }
                                val cust = debt?.let { d -> customers.firstOrNull { it.id == d.customerId } }
                                val items = allLineItems.filter { it.debtId == route.debtId }
                                DebtDetailScreen(
                                    debt = debt,
                                    customer = cust,
                                    lineItems = items,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onPayDebt = {
                                        if (debt != null) {
                                            preselectedCustomerId = debt.customerId
                                            preselectedDebtId = debt.id
                                            openPaymentDialogOnTab = true
                                            navigateTopTab(AppRoute.Payments)
                                        }
                                    },
                                    onEditDebt = { newTotal, due, notes ->
                                        if (debt != null) {
                                            vm.updateDebtDetails(debt.id, newTotal, due, notes, debt.receiptImageUri)
                                        }
                                    },
                                    onCancelDebt = {
                                        if (debt != null) vm.cancelDebt(debt.id)
                                    },
                                    onExportPdf = { shareWa, print ->
                                        if (debt != null && cust != null) {
                                            vm.exportInvoicePdf(debt, cust, shareWa, print)
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.Payments> {
                                PaymentsScreen(
                                    payments = payments,
                                    customers = customers,
                                    debts = debts,
                                    defaultCurrency = settings.defaultCurrency,
                                    defaultUsdToIlsRate = settings.usdToIlsRate,
                                    isArabic = isArabic,
                                    onOpenPayment = { id -> navController.navigate(AppRoute.PaymentDetail(id)) },
                                    onSubmitPayment = { cId, dId, amt, pCurr, tCurr, rate, method, pDate, notes, key, allowOver, cb ->
                                        vm.recordPayment(cId, dId, amt, pCurr, tCurr, rate, method, pDate, notes, key, allowOver, cb)
                                    },
                                    preselectedCustomerId = preselectedCustomerId,
                                    preselectedDebtId = preselectedDebtId,
                                    showAddDialogInitially = openPaymentDialogOnTab,
                                    onAddDialogConsumed = { openPaymentDialogOnTab = false }
                                )
                            }

                            composable<AppRoute.PaymentDetail> { entry ->
                                val route = entry.toRoute<AppRoute.PaymentDetail>()
                                val pay = payments.firstOrNull { it.id == route.paymentId }
                                val cust = pay?.let { p -> customers.firstOrNull { it.id == p.customerId } }
                                PaymentDetailScreen(
                                    payment = pay,
                                    customer = cust,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onEditPayment = { amt, curr, rate, method, notes ->
                                        if (pay != null) {
                                            vm.editPayment(pay.id, amt, curr, rate, method, pay.paymentDate, notes)
                                        }
                                    },
                                    onReversePayment = { reason ->
                                        if (pay != null) vm.reversePayment(pay.id, reason)
                                    },
                                    onExportReceiptPdf = { shareWa, print ->
                                        if (pay != null && cust != null) {
                                            vm.exportReceiptPdf(pay, cust, shareWa, print)
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.More> {
                                val lowStockCount = products.count { it.availableQty <= it.lowStockThreshold }
                                val pendingRemCount = reminders.count { it.status == ReminderStatus.SCHEDULED }
                                MoreHubScreen(
                                    isArabic = isArabic,
                                    lowStockCount = lowStockCount,
                                    pendingRemindersCount = pendingRemCount,
                                    onOpenProducts = { navController.navigate(AppRoute.Products) },
                                    onOpenInvoices = { navController.navigate(AppRoute.Invoices) },
                                    onOpenReports = { navController.navigate(AppRoute.Reports) },
                                    onOpenReminders = { navController.navigate(AppRoute.Reminders) },
                                    onOpenBackup = { navController.navigate(AppRoute.Backup) },
                                    onOpenSettings = { navController.navigate(AppRoute.Settings) },
                                    onOpenAuditLog = { navController.navigate(AppRoute.AuditLog) }
                                )
                            }

                            composable<AppRoute.Products> {
                                ProductsScreen(
                                    products = products,
                                    defaultCurrency = settings.defaultCurrency,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onSaveProduct = { vm.saveProduct(it) },
                                    onDeleteProduct = { vm.deleteProduct(it) },
                                    onImportCsv = { vm.importProductsCsv(it) },
                                    onExportCsv = { vm.exportProductsCsv(it) }
                                )
                            }

                            composable<AppRoute.Invoices> {
                                InvoicesScreen(
                                    debts = debts,
                                    payments = payments,
                                    customers = customers,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onOpenDebt = { id -> navController.navigate(AppRoute.DebtDetail(id)) },
                                    onOpenPayment = { id -> navController.navigate(AppRoute.PaymentDetail(id)) },
                                    onExportInvoicePdf = { d, c, wa, pr -> vm.exportInvoicePdf(d, c, wa, pr) },
                                    onExportReceiptPdf = { p, c, wa, pr -> vm.exportReceiptPdf(p, c, wa, pr) }
                                )
                            }

                            composable<AppRoute.Reports> {
                                ReportsScreen(
                                    dashboardSummary = dashboardSummary,
                                    customerSummaries = customerSummaries,
                                    debts = debts,
                                    lineItems = allLineItems,
                                    payments = payments,
                                    products = products,
                                    defaultCurrency = settings.defaultCurrency,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onExportReportPdf = { title, rows -> vm.exportReportPdf(title, rows) },
                                    onExportReportCsv = { uri, headers, rows -> vm.exportReportCsv(uri, headers, rows) }
                                )
                            }

                            composable<AppRoute.Reminders> {
                                RemindersScreen(
                                    reminders = reminders,
                                    customers = customers,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onCreateReminder = { cust, title, msg, due, ch ->
                                        vm.createReminder(cust, null, title, msg, due, ch)
                                    },
                                    onUpdateStatus = { id, status -> vm.markReminderStatus(id, status) },
                                    onDeleteReminder = { vm.deleteReminder(it) }
                                )
                            }

                            composable<AppRoute.Backup> {
                                BackupScreen(
                                    settings = settings,
                                    pendingRestore = pendingRestore,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() },
                                    onExportBackup = { uri, pass -> vm.exportBackup(uri, pass) },
                                    onInspectRestore = { uri, pass, onNeedPass -> vm.inspectRestoreBackup(uri, pass, onNeedPass) },
                                    onConfirmRestore = { mode -> vm.confirmRestore(mode) },
                                    onDismissPendingRestore = { vm.dismissPendingRestore() },
                                    onSetAutoBackup = { vm.setAutoBackupEnabled(it) }
                                )
                            }

                            composable<AppRoute.Settings> {
                                SettingsScreen(
                                    settings = settings,
                                    onBack = { navController.popBackStack() },
                                    onSaveProfile = { name, phone, logo -> vm.updateMerchantProfile(name, phone, logo) },
                                    onSetDefaultCurrency = { vm.setDefaultCurrency(it) },
                                    onSetManualRate = { vm.setManualExchangeRate(it) },
                                    onRefreshRateOnline = { vm.refreshExchangeRateOnline() },
                                    onSetLanguageArabic = { vm.setLanguageArabic(it) },
                                    onSetThemePreference = { vm.setThemePreference(it) },
                                    onSetNotificationsEnabled = { vm.setNotificationsEnabled(it) },
                                    onEnablePinLock = { pin, bio -> vm.enablePinLock(pin, bio) },
                                    onSetBiometricEnabled = { vm.setBiometricEnabled(it) },
                                    onDisableAppLock = { vm.disableAppLock() },
                                    onOpenBackup = { navController.navigate(AppRoute.Backup) }
                                )
                            }

                            composable<AppRoute.AuditLog> {
                                AuditLogScreen(
                                    logs = auditLogs,
                                    isArabic = isArabic,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showQuickTxMenu) {
            val ext = LocalDebtBookColors.current
            AlertDialog(
                onDismissRequest = { showQuickTxMenu = false },
                title = { Text(tr(isArabic, "إضافة معاملة سريعة", "Quick Transaction")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                showQuickTxMenu = false
                                preselectedCustomerId = null
                                openDebtDialogOnTab = true
                                navigateTopTab(AppRoute.Debts)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ext.crimson),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.AddCard, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr(isArabic, "تسجيل دين جديد", "Record New Debt"))
                        }
                        Button(
                            onClick = {
                                showQuickTxMenu = false
                                preselectedCustomerId = null
                                preselectedDebtId = null
                                openPaymentDialogOnTab = true
                                navigateTopTab(AppRoute.Payments)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ext.emerald),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr(isArabic, "تسجيل دفعة جديدة", "Record Payment"))
                        }
                        OutlinedButton(
                            onClick = {
                                showQuickTxMenu = false
                                openCustomerDialogOnTab = true
                                navigateTopTab(AppRoute.Customers)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr(isArabic, "إضافة زبون جديد", "Add Customer"))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showQuickTxMenu = false }) {
                        Text(tr(isArabic, "إغاء", "Close"))
                    }
                }
            )
        }
    }
}
