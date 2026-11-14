package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.money.CurrencyCode
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.DebtStatus
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.PaymentStatus
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers")
    suspend fun getAllOnce(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products")
    suspend fun getAllOnce(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("UPDATE products SET availableQty = availableQty - :delta, updatedAt = :now WHERE id = :productId AND availableQty >= :delta")
    suspend fun decrementStockIfAvailable(productId: String, delta: Int, now: Long): Int

    @Query("UPDATE products SET availableQty = availableQty + :delta, updatedAt = :now WHERE id = :productId")
    suspend fun incrementStock(productId: String, delta: Int, now: Long)

    @Delete
    suspend fun delete(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DebtEntity?>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE customerId = :customerId AND currency = :currency AND status IN ('OPEN', 'PARTIAL') ORDER BY createdAt ASC")
    suspend fun getOpenDebtsFifo(customerId: String, currency: CurrencyCode): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE customerId = :customerId AND status IN ('OPEN', 'PARTIAL')")
    suspend fun getOpenDebtsForCustomer(customerId: String): List<DebtEntity>

    @Query("SELECT * FROM debts")
    suspend fun getAllOnce(): List<DebtEntity>

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(debts: List<DebtEntity>)

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("SELECT * FROM debt_line_items ORDER BY id ASC")
    fun observeAllLineItems(): Flow<List<DebtLineItemEntity>>

    @Query("SELECT * FROM debt_line_items WHERE debtId = :debtId")
    fun observeLineItems(debtId: String): Flow<List<DebtLineItemEntity>>

    @Query("SELECT * FROM debt_line_items WHERE debtId = :debtId")
    suspend fun getLineItems(debtId: String): List<DebtLineItemEntity>

    @Query("SELECT * FROM debt_line_items")
    suspend fun getAllLineItemsOnce(): List<DebtLineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItems(items: List<DebtLineItemEntity>)

    @Query("DELETE FROM debt_line_items WHERE debtId = :debtId")
    suspend fun deleteLineItemsByDebt(debtId: String)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()

    @Query("DELETE FROM debt_line_items")
    suspend fun deleteAllLineItems()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY paymentDate DESC, createdAt DESC")
    fun observeByCustomer(customerId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(key: String): PaymentEntity?

    @Query("SELECT * FROM payments")
    suspend fun getAllOnce(): List<PaymentEntity>

    @Query("SELECT COUNT(*) FROM payments")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun getAllocationsForPayment(paymentId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations WHERE debtId = :debtId")
    suspend fun getAllocationsForDebt(debtId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations")
    fun observeAllAllocations(): Flow<List<PaymentAllocationEntity>>

    @Query("SELECT * FROM payment_allocations")
    suspend fun getAllAllocationsOnce(): List<PaymentAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<PaymentAllocationEntity>)

    @Query("DELETE FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun deleteAllocationsByPayment(paymentId: String)

    @Query("SELECT COALESCE(SUM(pa.allocatedMinor), 0) FROM payment_allocations pa INNER JOIN payments p ON pa.paymentId = p.id WHERE pa.debtId = :debtId AND p.status = 'COMPLETED'")
    suspend fun sumCompletedAllocatedForDebt(debtId: String): Long

    @Query("DELETE FROM payments")
    suspend fun deleteAll()

    @Query("DELETE FROM payment_allocations")
    suspend fun deleteAllAllocations()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllOnce(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReminderStatus)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}
