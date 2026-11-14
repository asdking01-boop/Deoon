package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.money.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "merchant_settings")

enum class ThemePreference {
    DARK,
    LIGHT,
    SYSTEM
}

data class MerchantSettings(
    val merchantName: String = "متجر الأمانة الحديث",
    val merchantPhone: String = "",
    val merchantLogoUri: String? = null,
    val defaultCurrency: CurrencyCode = CurrencyCode.ILS,
    val usdToIlsRate: String = "3.60",
    val lastRateUpdatedAt: Long = 0L,
    val isArabic: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val notificationsEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val autoBackupEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MERCHANT_NAME = stringPreferencesKey("merchant_name")
        val MERCHANT_PHONE = stringPreferencesKey("merchant_phone")
        val MERCHANT_LOGO_URI = stringPreferencesKey("merchant_logo_uri")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val USD_TO_ILS_RATE = stringPreferencesKey("usd_to_ils_rate")
        val LAST_RATE_UPDATED_AT = longPreferencesKey("last_rate_updated_at")
        val IS_ARABIC = booleanPreferencesKey("is_arabic")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    }

    val settingsFlow: Flow<MerchantSettings> = context.dataStore.data.map { prefs ->
        MerchantSettings(
            merchantName = prefs[Keys.MERCHANT_NAME] ?: "متجر الأمانة الحديث",
            merchantPhone = prefs[Keys.MERCHANT_PHONE] ?: "",
            merchantLogoUri = prefs[Keys.MERCHANT_LOGO_URI],
            defaultCurrency = CurrencyCode.fromCode(prefs[Keys.DEFAULT_CURRENCY]),
            usdToIlsRate = prefs[Keys.USD_TO_ILS_RATE] ?: "3.60",
            lastRateUpdatedAt = prefs[Keys.LAST_RATE_UPDATED_AT] ?: 0L,
            isArabic = prefs[Keys.IS_ARABIC] ?: true,
            themePreference = ThemePreference.entries.firstOrNull {
                it.name == prefs[Keys.THEME_PREF]
            } ?: ThemePreference.DARK,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT],
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false
        )
    }

    suspend fun updateMerchantProfile(name: String, phone: String, logoUri: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MERCHANT_NAME] = name.trim().ifEmpty { "متجر الأمانة الحديث" }
            prefs[Keys.MERCHANT_PHONE] = phone.trim()
            if (logoUri != null) {
                prefs[Keys.MERCHANT_LOGO_URI] = logoUri
            } else {
                prefs.remove(Keys.MERCHANT_LOGO_URI)
            }
        }
    }

    suspend fun setDefaultCurrency(currency: CurrencyCode) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = currency.code }
    }

    suspend fun setExchangeRate(rate: String, updatedAt: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[Keys.USD_TO_ILS_RATE] = rate
            it[Keys.LAST_RATE_UPDATED_AT] = updatedAt
        }
    }

    suspend fun setLanguageArabic(isArabic: Boolean) {
        context.dataStore.edit { it[Keys.IS_ARABIC] = isArabic }
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = pref.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }

    suspend fun enablePinLock(pin: String, biometricEnabled: Boolean) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, saltB64)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = saltB64
            it[Keys.PIN_HASH] = hash
            it[Keys.APP_LOCK_ENABLED] = true
            it[Keys.BIOMETRIC_ENABLED] = biometricEnabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun disableAppLock() {
        context.dataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = false
            it[Keys.BIOMETRIC_ENABLED] = false
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
        }
    }

    fun verifyPin(pin: String, settings: MerchantSettings): Boolean {
        val expectedHash = settings.pinHash ?: return false
        val salt = settings.pinSalt ?: return false
        return hashPin(pin, salt) == expectedHash
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
