package com.golden360.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "payroll_transactions",
    indices = [
        Index(value = ["reference"], unique = true),
        Index(value = ["employeeId", "monthKey"]),
        Index(value = ["type", "monthKey"])
    ]
)
data class PayrollTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reference: String,
    val employeeId: Long,
    val type: String,
    val monthKey: String,
    val amountPiasters: Long,
    val quantity: Int = 1,
    val note: String = "",
    val createdAtEpochMillis: Long
)

@Entity(tableName = "payroll_reference_counters", primaryKeys = ["type"])
data class PayrollReferenceCounter(
    val type: String,
    val lastSequence: Long
)

@Dao
interface PayrollTransactionDao {
    @Query("SELECT * FROM payroll_transactions WHERE employeeId = :employeeId AND monthKey = :monthKey ORDER BY createdAtEpochMillis DESC")
    suspend fun getForEmployeeMonth(employeeId: Long, monthKey: String): List<PayrollTransactionEntity>

    @Query("SELECT COALESCE(SUM(amountPiasters), 0) FROM payroll_transactions WHERE employeeId = :employeeId AND monthKey = :monthKey AND type = :type")
    suspend fun sumForEmployeeMonth(employeeId: Long, monthKey: String, type: String): Long

    @Query("SELECT * FROM payroll_transactions WHERE reference = :reference LIMIT 1")
    suspend fun getByReference(reference: String): PayrollTransactionEntity?

    @Query("SELECT * FROM payroll_reference_counters WHERE type = :type LIMIT 1")
    suspend fun getCounter(type: String): PayrollReferenceCounter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCounter(counter: PayrollReferenceCounter)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: PayrollTransactionEntity): Long

    @Transaction
    suspend fun create(
        employeeId: Long,
        type: TransactionType,
        monthKey: String,
        amountPiasters: Long,
        quantity: Int = 1,
        note: String = ""
    ): String {
        require(employeeId > 0) { "يجب اختيار موظف صحيح" }
        require(monthKey.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) { "الشهر يجب أن يكون بصيغة YYYY-MM" }
        require(amountPiasters > 0) { "قيمة الحركة يجب أن تكون أكبر من صفر" }
        require(quantity > 0) { "الكمية يجب أن تكون أكبر من صفر" }
        val sequence = (getCounter(type.name)?.lastSequence ?: 0L) + 1L
        val reference = TransactionReferenceFormatter.format(type, sequence)
        saveCounter(PayrollReferenceCounter(type.name, sequence))
        insert(
            PayrollTransactionEntity(
                reference = reference,
                employeeId = employeeId,
                type = type.name,
                monthKey = monthKey,
                amountPiasters = amountPiasters,
                quantity = quantity,
                note = note.trim(),
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        return reference
    }
}

@Database(
    entities = [PayrollTransactionEntity::class, PayrollReferenceCounter::class],
    version = 1,
    exportSchema = false
)
abstract class PayrollDatabase : RoomDatabase() {
    abstract fun transactions(): PayrollTransactionDao

    companion object {
        @Volatile private var instance: PayrollDatabase? = null

        fun get(context: Context): PayrollDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PayrollDatabase::class.java,
                "golden360_payroll.db"
            ).build().also { instance = it }
        }
    }
}
