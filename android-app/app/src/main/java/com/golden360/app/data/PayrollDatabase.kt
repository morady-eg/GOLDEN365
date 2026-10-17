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
    val createdAtEpochMillis: Long,
    val voidedAtEpochMillis: Long? = null,
    val voidReason: String? = null
)

@Entity(
    tableName = "daily_absences",
    primaryKeys = ["employeeId", "dateKey"],
    indices = [Index(value = ["employeeId", "monthKey"])]
)
data class DailyAbsenceEntity(
    val employeeId: Long,
    val dateKey: String,
    val monthKey: String,
    val markedAtEpochMillis: Long
)

@Entity(tableName = "monthly_attendance", primaryKeys = ["employeeId", "monthKey"])
data class MonthlyAttendanceEntity(val employeeId: Long, val monthKey: String, val absentDays: Int, @ColumnInfo(defaultValue = "0") val legacyAbsentDays: Int = 0)

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

@Entity(
    tableName = "payroll_audit_events",
    indices = [Index(value = ["employeeId", "createdAtEpochMillis"]), Index(value = ["transactionReference"])]
)
data class PayrollAuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val employeeId: Long,
    val monthKey: String,
    val transactionReference: String? = null,
    val beforeValue: String? = null,
    val afterValue: String,
    val actor: String = "LOCAL_DEVICE",
    val createdAtEpochMillis: Long
)

@Dao
interface PayrollTransactionDao {
    @Query("SELECT * FROM payroll_transactions WHERE employeeId = :employeeId AND monthKey = :monthKey ORDER BY createdAtEpochMillis DESC")
    suspend fun getForEmployeeMonth(employeeId: Long, monthKey: String): List<PayrollTransactionEntity>

    @Query("SELECT COALESCE(SUM(amountPiasters), 0) FROM payroll_transactions WHERE employeeId = :employeeId AND monthKey = :monthKey AND type = :type AND voidedAtEpochMillis IS NULL")
    suspend fun sumForEmployeeMonth(employeeId: Long, monthKey: String, type: String): Long

    @Query("SELECT * FROM payroll_transactions WHERE reference = :reference LIMIT 1")
    suspend fun getByReference(reference: String): PayrollTransactionEntity?

    @Query("UPDATE payroll_transactions SET voidedAtEpochMillis = :voidedAtEpochMillis, voidReason = :reason WHERE reference = :reference AND voidedAtEpochMillis IS NULL")
    suspend fun markTransactionVoided(reference: String, voidedAtEpochMillis: Long, reason: String): Int

    @Transaction
    suspend fun voidTransaction(reference: String, reason: String) {
        require(reference.isNotBlank()) { "الرقم المرجعي للحركة مطلوب" }
        require(reason.isNotBlank()) { "سبب إلغاء الحركة مطلوب للتدقيق" }
        val transaction = requireNotNull(getByReference(reference)) { "الحركة غير موجودة" }
        require(transaction.voidedAtEpochMillis == null) { "الحركة ملغاة بالفعل" }
        require(getPayrollSnapshot(transaction.employeeId, transaction.monthKey) == null) {
            "لا يمكن إلغاء حركة بعد اعتماد كشف الراتب"
        }
        val now = System.currentTimeMillis()
        val changed = markTransactionVoided(reference, now, reason.trim())
        check(changed == 1) { "تعذر إلغاء الحركة؛ ربما تم تعديلها من شاشة أخرى" }
        insertAuditEvent(
            PayrollAuditEventEntity(
                action = "TRANSACTION_VOIDED",
                employeeId = transaction.employeeId,
                monthKey = transaction.monthKey,
                transactionReference = transaction.reference,
                beforeValue = "type=${transaction.type};amountPiasters=${transaction.amountPiasters};quantity=${transaction.quantity}",
                afterValue = "voidedAtEpochMillis=${now};reason=${reason.trim()}",
                createdAtEpochMillis = now
            )
        )
    }

    @Query("SELECT * FROM payroll_reference_counters WHERE type = :type LIMIT 1")
    suspend fun getCounter(type: String): PayrollReferenceCounter?

    @Query("SELECT * FROM monthly_attendance WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1")
    suspend fun getAttendance(employeeId: Long, monthKey: String): MonthlyAttendanceEntity?

    @Query("SELECT * FROM daily_absences WHERE employeeId = :employeeId AND monthKey = :monthKey ORDER BY dateKey")
    suspend fun getDailyAbsences(employeeId: Long, monthKey: String): List<DailyAbsenceEntity>

    @Query("SELECT * FROM daily_absences WHERE employeeId = :employeeId AND dateKey = :dateKey LIMIT 1")
    suspend fun getDailyAbsence(employeeId: Long, dateKey: String): DailyAbsenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyAbsence(absence: DailyAbsenceEntity)

    @Query("DELETE FROM daily_absences WHERE employeeId = :employeeId AND dateKey = :dateKey")
    suspend fun deleteDailyAbsence(employeeId: Long, dateKey: String)

    @Transaction
    suspend fun setDailyAbsence(
        employeeId: Long,
        dateKey: String,
        monthKey: String,
        weeklyOffDaysCsv: String,
        absent: Boolean
    ) {
        require(employeeId > 0) { "يجب اختيار موظف صحيح" }
        val date = java.time.LocalDate.parse(dateKey)
        require(java.time.YearMonth.from(date).toString() == monthKey) { "تاريخ الغياب يجب أن يطابق الشهر المحدد" }
        require(!absent || !AttendancePolicy.isWeeklyOff(date, AttendancePolicy.parseWeeklyOffDays(weeklyOffDaysCsv))) {
            "الإجازة الأسبوعية مدفوعة الأجر ولا تسجل غيابًا"
        }
        require(getPayrollSnapshot(employeeId, monthKey) == null) { "لا يمكن تعديل الغياب بعد اعتماد كشف الراتب" }
        val previous = getDailyAbsence(employeeId, dateKey)
        if (absent && previous == null) {
            upsertDailyAbsence(DailyAbsenceEntity(employeeId, dateKey, monthKey, System.currentTimeMillis()))
        } else if (!absent && previous != null) {
            deleteDailyAbsence(employeeId, dateKey)
        }
        val allAbsences = getDailyAbsences(employeeId, monthKey)
        val previousMonthlyAttendance = getAttendance(employeeId, monthKey)
        val legacyDays = previousMonthlyAttendance?.legacyAbsentDays ?: 0
        val weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(weeklyOffDaysCsv)
        val effectiveDatedAbsences = allAbsences.count { row ->
            !AttendancePolicy.isWeeklyOff(java.time.LocalDate.parse(row.dateKey), weeklyOffDays)
        }
        require(legacyDays + effectiveDatedAbsences <= 30) {
            "إجمالي أيام الغياب لا يمكن أن يتجاوز 30 يومًا في سياسة المرتب الحالية"
        }
        upsertAttendance(MonthlyAttendanceEntity(employeeId, monthKey, legacyDays + effectiveDatedAbsences, legacyDays))
        if ((absent && previous == null) || (!absent && previous != null)) {
            insertAuditEvent(
                PayrollAuditEventEntity(
                    action = if (absent) "ABSENCE_DATE_MARKED" else "ABSENCE_DATE_CLEARED",
                    employeeId = employeeId,
                    monthKey = monthKey,
                    beforeValue = if (previous == null) "PRESENT" else "ABSENT",
                    afterValue = if (absent) "ABSENT:$dateKey" else "PRESENT:$dateKey",
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(attendance: MonthlyAttendanceEntity)

    @Query("SELECT * FROM payroll_audit_events WHERE employeeId = :employeeId AND monthKey = :monthKey ORDER BY createdAtEpochMillis DESC, id DESC")
    suspend fun getAuditEvents(employeeId: Long, monthKey: String): List<PayrollAuditEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditEvent(event: PayrollAuditEventEntity): Long

    @Transaction
    suspend fun saveAttendance(attendance: MonthlyAttendanceEntity) {
        require(attendance.absentDays in 0..30) { "أيام الغياب يجب أن تكون من 0 إلى 30" }
        require(getPayrollSnapshot(attendance.employeeId, attendance.monthKey) == null) {
            "لا يمكن تعديل الغياب بعد اعتماد كشف الراتب"
        }
        val previous = getAttendance(attendance.employeeId, attendance.monthKey)
        upsertAttendance(attendance)
        if (previous?.absentDays != attendance.absentDays) {
            insertAuditEvent(
                PayrollAuditEventEntity(
                    action = "ABSENCE_UPDATED",
                    employeeId = attendance.employeeId,
                    monthKey = attendance.monthKey,
                    beforeValue = previous?.absentDays?.toString(),
                    afterValue = attendance.absentDays.toString(),
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }
    @Query("SELECT * FROM payroll_snapshots WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1")
    suspend fun getPayrollSnapshot(employeeId: Long, monthKey: String): PayrollSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayrollSnapshot(snapshot: PayrollSnapshotEntity)

    @Transaction
    suspend fun approvePayrollSnapshot(snapshot: PayrollSnapshotEntity): PayrollSnapshotEntity {
        require(getPayrollSnapshot(snapshot.employeeId, snapshot.monthKey) == null) {
            "كشف الراتب معتمد بالفعل لهذا الموظف والشهر"
        }
        insertPayrollSnapshot(snapshot)
        insertAuditEvent(
            PayrollAuditEventEntity(
                action = "PAYROLL_APPROVED",
                employeeId = snapshot.employeeId,
                monthKey = snapshot.monthKey,
                afterValue = "netSalaryPiasters=${snapshot.netSalaryPiasters};absentDays=${snapshot.absentDays}",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        return snapshot
    }

    @Transaction
    suspend fun approvePayrollSnapshots(snapshots: List<PayrollSnapshotEntity>): Int {
        require(snapshots.isNotEmpty()) { "لا توجد كشوف جديدة للاعتماد" }
        val monthKeys = snapshots.map { it.monthKey }.distinct()
        require(monthKeys.size == 1 && monthKeys.single().matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
            "يجب أن تنتمي جميع الكشوف إلى شهر واحد صحيح"
        }
        require(snapshots.map { it.employeeId }.distinct().size == snapshots.size) {
            "يوجد موظف مكرر في قائمة الاعتماد"
        }
        snapshots.forEach { snapshot ->
            require(snapshot.employeeId > 0) { "معرّف الموظف غير صحيح" }
            require(getPayrollSnapshot(snapshot.employeeId, snapshot.monthKey) == null) {
                "كشف الراتب معتمد بالفعل لأحد الموظفين؛ حدّث الكشف وحاول مجددًا"
            }
        }
        val now = System.currentTimeMillis()
        snapshots.forEach { snapshot ->
            insertPayrollSnapshot(snapshot.copy(approvedAtEpochMillis = now))
            insertAuditEvent(
                PayrollAuditEventEntity(
                    action = "PAYROLL_APPROVED",
                    employeeId = snapshot.employeeId,
                    monthKey = snapshot.monthKey,
                    afterValue = "netSalaryPiasters=${snapshot.netSalaryPiasters};absentDays=${snapshot.absentDays};batch=true",
                    createdAtEpochMillis = now
                )
            )
        }
        return snapshots.size
    }

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
            require(overtimeTime in setOf("صباحية", "مسائية") || overtimeTime.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) { "اختر صباحية أو مسائية للوردية الإضافية" }
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
        insertAuditEvent(
            PayrollAuditEventEntity(
                action = "TRANSACTION_CREATED",
                employeeId = employeeId,
                monthKey = monthKey,
                transactionReference = reference,
                afterValue = "type=${type.name};amountPiasters=${amountPiasters};quantity=${quantity}",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        return reference
    }
}

@Database(
    entities = [PayrollTransactionEntity::class, DailyAbsenceEntity::class, MonthlyAttendanceEntity::class, PayrollSnapshotEntity::class, PayrollReferenceCounter::class, PayrollAuditEventEntity::class],
    version = 6,
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
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS payroll_audit_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, action TEXT NOT NULL, employeeId INTEGER NOT NULL, monthKey TEXT NOT NULL, transactionReference TEXT, beforeValue TEXT, afterValue TEXT NOT NULL, actor TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_audit_events_employeeId_createdAtEpochMillis ON payroll_audit_events(employeeId, createdAtEpochMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_audit_events_transactionReference ON payroll_audit_events(transactionReference)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS daily_absences (employeeId INTEGER NOT NULL, dateKey TEXT NOT NULL, monthKey TEXT NOT NULL, markedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(employeeId, dateKey))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_daily_absences_employeeId_monthKey ON daily_absences(employeeId, monthKey)")
                database.execSQL("ALTER TABLE monthly_attendance ADD COLUMN legacyAbsentDays INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE monthly_attendance SET legacyAbsentDays = absentDays")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE payroll_transactions ADD COLUMN voidedAtEpochMillis INTEGER")
                database.execSQL("ALTER TABLE payroll_transactions ADD COLUMN voidReason TEXT")
            }
        }

        @Volatile private var instance: PayrollDatabase? = null

        @Synchronized
        internal fun closeForBackup() {
            instance?.close()
            instance = null
        }

        fun get(context: Context): PayrollDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val passphrase = DatabaseEncryption.prepareEncryptedDatabase(context.applicationContext, "golden360_payroll.db")
                Room.databaseBuilder(
                    context.applicationContext,
                    PayrollDatabase::class.java,
                    "golden360_payroll.db"
                ).openHelperFactory(SupportFactory(passphrase)).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { instance = it }
            }
        }
    }
}
