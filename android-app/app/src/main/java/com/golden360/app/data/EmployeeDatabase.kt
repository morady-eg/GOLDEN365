package com.golden360.app.data

import android.content.Context
import net.sqlcipher.database.SupportFactory
import androidx.room.Dao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.ColumnInfo
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
import androidx.room.Update

@Entity(tableName = "employees", indices = [Index(value = ["code"], unique = true)])
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val fullName: String,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "'08:00'") val shiftStartTime: String = "08:00",
    @ColumnInfo(defaultValue = "''") val weeklyOffDays: String = "",
    val jobTitle: String,
    val hireDate: String?,
    val nationalId: String?,
    val address: String?,
    val profilePhotoUri: String?,
    val nationalIdFrontUri: String?,
    val nationalIdBackUri: String?,
    val extraShiftRatePiasters: Long,
    @ColumnInfo(defaultValue = "0") val baseSalaryPiasters: Long = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "'ACTIVE'") val workStatus: String = "ACTIVE",
    val statusEffectiveDate: String? = null,
    @ColumnInfo(defaultValue = "''") val suspensionPeriods: String = ""
)

@Entity(tableName = "employee_code_counter")
data class EmployeeCodeCounter(@PrimaryKey val key: Int = 1, val lastSequence: Long)

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE archived = 0 ORDER BY fullName COLLATE NOCASE")
    suspend fun getActiveEmployees(): List<EmployeeEntity>

    @Query("SELECT * FROM employees ORDER BY archived ASC, fullName COLLATE NOCASE")
    suspend fun getAllEmployees(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(employee: EmployeeEntity): Long

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCounter(counter: EmployeeCodeCounter)

    @Transaction
    suspend fun suspendEmployee(id: Long, effectiveDate: String = java.time.LocalDate.now().toString()) {
        val employee = requireNotNull(getById(id)) { "الموظف غير موجود" }
        require(employee.workStatus == "ACTIVE") { "لا يمكن إيقاف الموظف مؤقتًا إلا إذا كان على رأس العمل" }
        val date = java.time.LocalDate.parse(effectiveDate)
        update(employee.copy(
            workStatus = "SUSPENDED",
            statusEffectiveDate = date.toString(),
            suspensionPeriods = AttendancePolicy.startSuspension(employee.suspensionPeriods, date),
            updatedAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Transaction
    suspend fun resumeEmployee(id: Long, effectiveDate: String = java.time.LocalDate.now().toString()) {
        val employee = requireNotNull(getById(id)) { "الموظف غير موجود" }
        require(employee.workStatus == "SUSPENDED") { "الموظف ليس موقوفًا مؤقتًا" }
        val date = java.time.LocalDate.parse(effectiveDate)
        update(employee.copy(
            workStatus = "ACTIVE",
            statusEffectiveDate = date.toString(),
            suspensionPeriods = AttendancePolicy.endSuspension(employee.suspensionPeriods, date),
            updatedAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Transaction
    suspend fun terminateEmployee(id: Long, effectiveDate: String = java.time.LocalDate.now().toString()) {
        val employee = requireNotNull(getById(id)) { "الموظف غير موجود" }
        require(employee.workStatus != "TERMINATED") { "تم إنهاء عمل الموظف بالفعل" }
        val date = java.time.LocalDate.parse(effectiveDate)
        val history = if (employee.workStatus == "SUSPENDED") {
            AttendancePolicy.endSuspension(employee.suspensionPeriods, date)
        } else employee.suspensionPeriods
        update(employee.copy(
            workStatus = "TERMINATED",
            statusEffectiveDate = date.toString(),
            suspensionPeriods = history,
            updatedAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Query("SELECT * FROM employee_code_counter WHERE `key` = 1 LIMIT 1")
    suspend fun getCounter(): EmployeeCodeCounter?

    @Query("SELECT MAX(CAST(SUBSTR(code, 4) AS INTEGER)) FROM employees")
    suspend fun highestExistingSequence(): Long?

    @Transaction
    suspend fun createEmployee(draft: EmployeeDraft): Long {
        val validation = EmployeeValidator.validate(draft)
        require(validation.isValid) { validation.errors.values.joinToString("، ") }
        val now = System.currentTimeMillis()
        val counter = getCounter()?.lastSequence ?: (highestExistingSequence() ?: 0L)
        val next = Math.addExact(counter, 1L)
        saveCounter(EmployeeCodeCounter(lastSequence = next))
        val rate = requireNotNull(EmployeeValidator.toPiasters(draft.extraShiftRate))
        return insert(
            EmployeeEntity(
                code = EmployeeCodeFormatter.format(next),
                fullName = draft.fullName.trim(),
                phone = draft.phone.trim(),
                shiftStartTime = draft.shiftStartTime.trim(),
                weeklyOffDays = AttendancePolicy.serializeWeeklyOffDays(draft.weeklyOffDays),
                jobTitle = requireNotNull(draft.jobTitle).name,
                hireDate = draft.hireDate.trim().ifEmpty { null },
                nationalId = draft.nationalId.trim().ifEmpty { null },
                address = draft.address.trim().ifEmpty { null },
                profilePhotoUri = draft.profilePhotoUri,
                nationalIdFrontUri = draft.nationalIdFrontUri,
                nationalIdBackUri = draft.nationalIdBackUri,
                extraShiftRatePiasters = rate,
                baseSalaryPiasters = requireNotNull(EmployeeValidator.toPiasters(draft.baseSalary)),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }

    @Transaction
    suspend fun updateEmployee(id: Long, draft: EmployeeDraft) {
        val validation = EmployeeValidator.validate(draft)
        require(validation.isValid) { validation.errors.values.joinToString("، ") }
        val old = requireNotNull(getById(id)) { "الموظف غير موجود" }
        update(
            old.copy(
                fullName = draft.fullName.trim(),
                phone = draft.phone.trim(),
                shiftStartTime = draft.shiftStartTime.trim(),
                weeklyOffDays = AttendancePolicy.serializeWeeklyOffDays(draft.weeklyOffDays),
                jobTitle = requireNotNull(draft.jobTitle).name,
                hireDate = draft.hireDate.trim().ifEmpty { null },
                nationalId = draft.nationalId.trim().ifEmpty { null },
                address = draft.address.trim().ifEmpty { null },
                profilePhotoUri = draft.profilePhotoUri,
                nationalIdFrontUri = draft.nationalIdFrontUri,
                nationalIdBackUri = draft.nationalIdBackUri,
                extraShiftRatePiasters = requireNotNull(EmployeeValidator.toPiasters(draft.extraShiftRate)),
                baseSalaryPiasters = requireNotNull(EmployeeValidator.toPiasters(draft.baseSalary)),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }
}

@Database(entities = [EmployeeEntity::class, EmployeeCodeCounter::class], version = 5, exportSchema = false)
abstract class EmployeeDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE employees ADD COLUMN baseSalaryPiasters INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE employees ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE employees ADD COLUMN shiftStartTime TEXT NOT NULL DEFAULT '08:00'")
                database.execSQL("ALTER TABLE employees ADD COLUMN weeklyOffDays TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE employees ADD COLUMN workStatus TEXT NOT NULL DEFAULT 'ACTIVE'")
                database.execSQL("ALTER TABLE employees ADD COLUMN statusEffectiveDate TEXT")
                database.execSQL("ALTER TABLE employees ADD COLUMN suspensionPeriods TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var INSTANCE: EmployeeDatabase? = null

        @Synchronized
        internal fun closeForBackup() {
            INSTANCE?.close()
            INSTANCE = null
        }

        fun get(context: Context): EmployeeDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val passphrase = DatabaseEncryption.prepareEncryptedDatabase(context.applicationContext, "golden360.db")
                Room.databaseBuilder(
                    context.applicationContext,
                    EmployeeDatabase::class.java,
                    "golden360.db"
                ).openHelperFactory(SupportFactory(passphrase)).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
