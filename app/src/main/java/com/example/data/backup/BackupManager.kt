package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.example.data.local.DebtBookDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReminderEntity
import com.example.domain.model.BackupConflictInfo
import com.example.domain.model.RestoreMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class DatabaseBackupSnapshot(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val appName: String = "SmartDebtBook",
    val customers: List<CustomerEntity>,
    val products: List<ProductEntity>,
    val debts: List<DebtEntity>,
    val debtLineItems: List<DebtLineItemEntity>,
    val payments: List<PaymentEntity>,
    val paymentAllocations: List<PaymentAllocationEntity>,
    val reminders: List<ReminderEntity>,
    val auditLogs: List<AuditLogEntity>
)

@Serializable
data class EncryptedEnvelope(
    val format: String = "SMART_DEBT_BOOK_ENC_V1",
    val saltB64: String,
    val ivB64: String,
    val ciphertextB64: String
)

class BackupManager(
    private val context: Context,
    private val db: DebtBookDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createSnapshot(): DatabaseBackupSnapshot = withContext(Dispatchers.IO) {
        DatabaseBackupSnapshot(
            createdAt = System.currentTimeMillis(),
            customers = db.customerDao().getAllOnce(),
            products = db.productDao().getAllOnce(),
            debts = db.debtDao().getAllOnce(),
            debtLineItems = db.debtDao().getAllLineItemsOnce(),
            payments = db.paymentDao().getAllOnce(),
            paymentAllocations = db.paymentDao().getAllAllocationsOnce(),
            reminders = db.reminderDao().getAllOnce(),
            auditLogs = db.auditLogDao().getAllOnce()
        )
    }

    suspend fun exportToUri(uri: Uri, passphrase: String?): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = createSnapshot()
            val plainJson = json.encodeToString(snapshot)
            val outputBytes = if (!passphrase.isNullOrBlank()) {
                val envelope = encryptString(plainJson, passphrase)
                json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            } else {
                plainJson.toByteArray(Charsets.UTF_8)
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(outputBytes)
                out.flush()
            } ?: error("Cannot open output stream")
            snapshot.createdAt
        }
    }

    suspend fun parseBackupFromUri(uri: Uri, passphrase: String?): Result<DatabaseBackupSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Cannot read backup file")

                val plainJson = if (raw.contains("SMART_DEBT_BOOK_ENC_V1")) {
                    require(!passphrase.isNullOrBlank()) { "PASSPHRASE_REQUIRED" }
                    val envelope = json.decodeFromString<EncryptedEnvelope>(raw)
                    decryptString(envelope, passphrase)
                } else {
                    raw
                }
                json.decodeFromString<DatabaseBackupSnapshot>(plainJson)
            }
        }

    suspend fun inspectConflict(snapshot: DatabaseBackupSnapshot): BackupConflictInfo =
        withContext(Dispatchers.IO) {
            val localCustomers = db.customerDao().getAllOnce()
            val localDebts = db.debtDao().getAllOnce()
            val localPayments = db.paymentDao().getAllOnce()
            val latestLocal = maxOf(
                localCustomers.maxOfOrNull { it.updatedAt } ?: 0L,
                localDebts.maxOfOrNull { it.updatedAt } ?: 0L,
                localPayments.maxOfOrNull { it.updatedAt } ?: 0L
            )
            BackupConflictInfo(
                backupCreatedAt = snapshot.createdAt,
                backupCustomersCount = snapshot.customers.size,
                backupDebtsCount = snapshot.debts.size,
                backupPaymentsCount = snapshot.payments.size,
                backupProductsCount = snapshot.products.size,
                localCustomersCount = localCustomers.size,
                localDebtsCount = localDebts.size,
                localPaymentsCount = localPayments.size,
                hasNewerLocalData = latestLocal > snapshot.createdAt,
                latestLocalTimestamp = latestLocal
            )
        }

    suspend fun restoreSnapshot(snapshot: DatabaseBackupSnapshot, mode: RestoreMode): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                db.withTransaction {
                    when (mode) {
                        RestoreMode.REPLACE_ALL -> {
                            db.paymentDao().deleteAllAllocations()
                            db.paymentDao().deleteAll()
                            db.debtDao().deleteAllLineItems()
                            db.debtDao().deleteAll()
                            db.reminderDao().deleteAll()
                            db.productDao().deleteAll()
                            db.customerDao().deleteAll()
                            db.auditLogDao().deleteAll()

                            db.customerDao().upsertAll(snapshot.customers)
                            db.productDao().upsertAll(snapshot.products)
                            db.debtDao().upsertAll(snapshot.debts)
                            db.debtDao().insertLineItems(snapshot.debtLineItems)
                            db.paymentDao().upsertAll(snapshot.payments)
                            db.paymentDao().insertAllocations(snapshot.paymentAllocations)
                            db.reminderDao().upsertAll(snapshot.reminders)
                            db.auditLogDao().upsertAll(snapshot.auditLogs)
                        }
                        RestoreMode.MERGE_KEEP_NEWER -> {
                            val localCustomers = db.customerDao().getAllOnce().associateBy { it.id }
                            val mergedCustomers = snapshot.customers.filter { incoming ->
                                val local = localCustomers[incoming.id]
                                local == null || incoming.updatedAt > local.updatedAt
                            }
                            db.customerDao().upsertAll(mergedCustomers)

                            val localProducts = db.productDao().getAllOnce().associateBy { it.id }
                            val mergedProducts = snapshot.products.filter { incoming ->
                                val local = localProducts[incoming.id]
                                local == null || incoming.updatedAt > local.updatedAt
                            }
                            db.productDao().upsertAll(mergedProducts)

                            val localDebts = db.debtDao().getAllOnce().associateBy { it.id }
                            val mergedDebts = snapshot.debts.filter { incoming ->
                                val local = localDebts[incoming.id]
                                local == null || incoming.updatedAt > local.updatedAt
                            }
                            db.debtDao().upsertAll(mergedDebts)
                            val mergedDebtIds = mergedDebts.map { it.id }.toSet()
                            db.debtDao().insertLineItems(snapshot.debtLineItems.filter { it.debtId in mergedDebtIds })

                            val localPayments = db.paymentDao().getAllOnce().associateBy { it.id }
                            val mergedPayments = snapshot.payments.filter { incoming ->
                                val local = localPayments[incoming.id]
                                local == null || incoming.updatedAt > local.updatedAt
                            }
                            db.paymentDao().upsertAll(mergedPayments)
                            val mergedPaymentIds = mergedPayments.map { it.id }.toSet()
                            db.paymentDao().insertAllocations(snapshot.paymentAllocations.filter { it.paymentId in mergedPaymentIds })

                            val localReminders = db.reminderDao().getAllOnce().map { it.id }.toSet()
                            db.reminderDao().upsertAll(snapshot.reminders.filter { it.id !in localReminders })
                        }
                    }
                    db.auditLogDao().insert(
                        AuditLogEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = "BACKUP",
                            entityId = "restore",
                            action = "RESTORE",
                            summary = "Restored backup (${mode.name}) from ${snapshot.createdAt}",
                            timestamp = now
                        )
                    )
                }
            }
        }

    private fun encryptString(plaintext: String, passphrase: String): EncryptedEnvelope {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        )
    }

    private fun decryptString(envelope: EncryptedEnvelope, passphrase: String): String {
        val salt = Base64.decode(envelope.saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(envelope.ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.ciphertextB64, Base64.NO_WRAP)
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val algo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            "PBKDF2WithHmacSHA256"
        } else {
            "PBKDF2WithHmacSHA1"
        }
        val factory = SecretKeyFactory.getInstance(algo)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
