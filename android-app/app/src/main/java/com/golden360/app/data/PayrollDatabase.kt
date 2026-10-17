package com.golden360.app.data

import android.content.Context
import net.sqlcipher.database.SupportFactory
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    @ColumnInfo(defaultValue = "''") val overtimePlace: String = "",
    @ColumnInfo(defaultValue = "''") val overtimeDate: String = "",
    @ColumnInfo(defaultValue = "''") val overtimeTime: String = "",
    val createdAtEpochMillis: Long
)

@Entity(tableName = "monthly_attendance", primaryKeys = ["employeeId", "monthKey"])
data class MonthlyAttendanceEntity(val employeeId: Long, val monthKey: String, val absentDays: Int)

@Entity(tableName = "payroll_snapshots", primaryKeys = ["employeeId", "monthKey"])
data class PayrollSnapshotEntity(
    val employeeId: Long, val monthKey: String, val employeeName: String, val employeeCode: String, val jobTitle: String,
    val baseSalaryPiasters: Long, val absentDays: Int, val absenceDeductionPiasters: Long, val advancesPiasters: Long,
    val penaltiesPiasters: Long, val overtimePiasters: Long, val bonusesPiasters: Long, val netSalaryPiasters: Long,
    val approvedAtEpochMillis: Long
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

    @Query("SELECT * FROM monthly_attendance WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1")
    suspend fun getAttendance(employeeId: Long, monthKey: String): MonthlyAttendanceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(attendance: MonthlyAttendanceEntity)

    @Transaction
    suspend fun saveAttendance(attendance: MonthlyAttendanceEntity) {
        require(attendance.absentDays in 0..30) { "أيام الغياب يجب أن تكون من 0 إلى 30" }
        require(getPayrollSnapshot(attendance.employeeId, attendance.monthKey) == null) {
            "لا يمكن تعديل الغياب بعد اعتماد كشف الراتب"
        }
        upsertAttendance(attendance)
    }
    @Query("SELECT * FROM payroll_snapshots WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1")
    suspend fun getPayrollSnapshot(employeeId: Long, monthKey: String): PayrollSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayrollSnapshot(snapshot: PayrollSnapshotEntity)

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
        note: String = "",
        overtimePlace: String = "", overtimeDate: String = "", overtimeTime: String = ""
    ): String {
        require(employeeId > 0) { "يجب اختيار موظف صحيح" }
        require(monthKey.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) { "الشهر يجب أن يكون بصيغة YYYY-MM" }
        require(amountPiasters > 0) { "قيمة الحركة يجب أن تكون أكبر من صفر" }
        require(quantity > 0) { "الكمية يجب أن تكون أكبر من صفر" }
        require(getPayrollSnapshot(employeeId, monthKey) == null) { "لا يمكن إضافة حركة إلى شهر تم اعتماد كشف راتبه" }
        if (type == TransactionType.OVERTIME) {
            require(overtimePlace.isNotBlank()) { "مكان الوردية مطلوب" }
            require(overtimeTime.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) { "موعد الوردية يجب أن يكون HH:mm" }
            val date = java.time.LocalDate.parse(overtimeDate)
            require(java.time.YearMonth.from(date).toString() == monthKey) { "شهر الحركة يجب أن يطابق شهر الوردية" }
        }
        val sequence = Math.addExact(getCounter(type.name)?.lastSequence ?: 0L, 1L)
        val reference = TransactionReferenceFormatter.format(type, sequence)
        saveCounter(PayrollReferenceCounter(type.name, sequence))
        insert(
            PayrollTransactionEntity(
                reference = reference,
                employeeId = employeeId,
                type = type.name,
                monthKey = monthKey,
                amountPiasters = amountPiasters,
                quantity = quantity, note = note.trim(), overtimePlace = overtimePlace.trim(),
                overtimeDate = overtimeDate.trim(), overtimeTime = overtimeTime.trim(),
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        return reference
    }
}

@Database(
    entities = [PayrollTransactionEntity::class, MonthlyAttendanceEntity::class, PayrollSnapshotEntity::class, PayrollReferenceCounter::class],
    version = 3,
    exportSchema = false
)
abstract class PayrollDatabase : RoomDatabase() {
    abstract fun transactions(): PayrollTransactionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE payroll_transactions ADD COLUMN overtimePlace TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE payroll_transactions ADD COLUMN overtimeDate TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE payroll_transactions ADD COLUMN overtimeTime TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE TABLE IF NOT EXISTS monthly_attendance (employeeId INTEGER NOT NULL, monthKey TEXT NOT NULL, absentDays INTEGER NOT NULL, PRIMARY KEY(employeeId, monthKey))")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS payroll_snapshots (employeeId INTEGER NOT NULL, monthKey TEXT NOT NULL, employeeName TEXT NOT NULL, employeeCode TEXT NOT NULL, jobTitle TEXT NOT NULL, baseSalaryPiasters INTEGER NOT NULL, absentDays INTEGER NOT NULL, absenceDeductionPiasters INTEGER NOT NULL, advancesPiasters INTEGER NOT NULL, penaltiesPiasters INTEGER NOT NULL, overtimePiasters INTEGER NOT NULL, bonusesPiasters INTEGER NOT NULL, netSalaryPiasters INTEGER NOT NULL, approvedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(employeeId, monthKey))")
            }
        }
        @Volatile private var instance: PayrollDatabase? = null

        fun get(context: Context): PayrollDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val passphrase = DatabaseEncryption.prepareEncryptedDatabase(context.applicationContext, "golden360_payroll.db")
                Room.databaseBuilder(
                    context.applicationContext,
                    PayrollDatabase::class.java,
                    "golden360_payroll.db"
                ).openHelperFactory(SupportFactory(passphrase)).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
        }
    }
}
