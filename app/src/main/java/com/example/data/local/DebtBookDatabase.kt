package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.DebtDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.PaymentAllocationEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReminderEntity

@Database(
    entities = [
        CustomerEntity::class,
        ProductEntity::class,
        DebtEntity::class,
        DebtLineItemEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        ReminderEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class DebtBookDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DATABASE_NAME = "smart_debt_book.db"

        /**
         * Schema migration scaffold for future upgrades without losing merchant financial history.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future non-destructive schema upgrades.
            }
        }

        fun build(context: Context): DebtBookDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DebtBookDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
