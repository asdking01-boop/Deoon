package com.example

import android.app.Application
import com.example.data.backup.BackupManager
import com.example.data.local.DebtBookDatabase
import com.example.data.network.ExchangeRateService
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.LedgerRepository
import com.example.notifications.ReminderNotificationHelper

class AppContainer(application: Application) {
    val database: DebtBookDatabase by lazy { DebtBookDatabase.build(application) }
    val repository: LedgerRepository by lazy { LedgerRepository(database) }
    val settingsStore: SettingsDataStore by lazy { SettingsDataStore(application) }
    val backupManager: BackupManager by lazy { BackupManager(application, database) }
    val exchangeRateService: ExchangeRateService by lazy { ExchangeRateService() }
}

class DebtBookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotificationHelper.ensureChannel(this)
    }
}
