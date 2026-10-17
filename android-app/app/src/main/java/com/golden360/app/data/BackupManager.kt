package com.golden360.app.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, password-encrypted backup for both SQLCipher Room databases.
 * Restore validates both databases before replacing anything and rolls files back on failure.
 */
internal object BackupManager {
    private const val EMPLOYEE_DB = "golden360.db"
    private const val PAYROLL_DB = "golden360_payroll.db"
    private const val MAX_BACKUP_BYTES = 200 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 100 * 1024 * 1024
    private val ALLOWED_ENTRIES = setOf("format.txt", "employees.db", "employees.key", "payroll.db", "payroll.key")

    fun createEncryptedBackup(context: Context, password: CharArray): ByteArray {
        require(password.size >= 10) { "استخدم كلمة مرور من 10 أحرف على الأقل" }
        val app = context.applicationContext
        val employeePassphrase = DatabaseEncryption.currentPassphrase(app, EMPLOYEE_DB)
        val payrollPassphrase = DatabaseEncryption.currentPassphrase(app, PAYROLL_DB)
        closeDatabases()
        try {
            val employeeFile = app.getDatabasePath(EMPLOYEE_DB)
            val payrollFile = app.getDatabasePath(PAYROLL_DB)
            check(employeeFile.isFile && employeeFile.length() > 0L) { "قاعدة بيانات الموظفين غير موجودة" }
            check(payrollFile.isFile && payrollFile.length() > 0L) { "قاعدة بيانات الرواتب غير موجودة" }
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { out ->
                addEntry(out, "format.txt", "GOLDEN360_BACKUP_V1".toByteArray(StandardCharsets.UTF_8))
                addEntry(out, "employees.db", employeeFile.readBytes())
                addEntry(out, "employees.key", employeePassphrase.toByteArray(StandardCharsets.UTF_8))
                addEntry(out, "payroll.db", payrollFile.readBytes())
                addEntry(out, "payroll.key", payrollPassphrase.toByteArray(StandardCharsets.UTF_8))
            }
            check(zip.size() <= MAX_BACKUP_BYTES) { "حجم النسخة الاحتياطية أكبر من الحد المسموح" }
            return BackupCrypto.encrypt(zip.toByteArray(), password)
        } finally {
            reopenDatabases(app)
        }
    }

    fun restoreEncryptedBackup(context: Context, encryptedBackup: ByteArray, password: CharArray) {
        require(encryptedBackup.size <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح" }
        val app = context.applicationContext
        val plainZip = BackupCrypto.decrypt(encryptedBackup, password)
        require(plainZip.size <= MAX_BACKUP_BYTES) { "محتوى النسخة الاحتياطية أكبر من الحد المسموح" }
        val entries = readAndValidateArchive(plainZip)
        val employeePassphrase = entries.getValue("employees.key").toString(StandardCharsets.UTF_8)
        val payrollPassphrase = entries.getValue("payroll.key").toString(StandardCharsets.UTF_8)
        require(employeePassphrase.isNotBlank() && payrollPassphrase.isNotBlank()) { "مفاتيح قواعد البيانات غير صالحة" }

        val staging = File(app.cacheDir, "golden360-restore-staging").apply {
            deleteRecursively()
            check(mkdirs()) { "تعذر تجهيز الاستعادة الآمنة" }
        }
        val employeeStage = File(staging, EMPLOYEE_DB)
        val payrollStage = File(staging, PAYROLL_DB)
        try {
            employeeStage.writeBytes(entries.getValue("employees.db"))
            payrollStage.writeBytes(entries.getValue("payroll.db"))
            validateEncryptedDatabase(employeeStage, employeePassphrase)
            validateEncryptedDatabase(payrollStage, payrollPassphrase)

            val employeeFile = app.getDatabasePath(EMPLOYEE_DB)
            val payrollFile = app.getDatabasePath(PAYROLL_DB)
            employeeFile.parentFile?.let { check(it.exists() || it.mkdirs()) { "تعذر الوصول إلى مجلد البيانات" } }
            val employeeRollback = File(staging, "employees.previous")
            val payrollRollback = File(staging, "payroll.previous")
            if (employeeFile.exists()) check(employeeFile.copyTo(employeeRollback, overwrite = true).exists())
            if (payrollFile.exists()) check(payrollFile.copyTo(payrollRollback, overwrite = true).exists())

            val oldEmployeePassphrase = DatabaseEncryption.currentPassphrase(app, EMPLOYEE_DB)
            val oldPayrollPassphrase = DatabaseEncryption.currentPassphrase(app, PAYROLL_DB)
            closeDatabases()
            try {
                deleteSidecars(employeeFile)
                deleteSidecars(payrollFile)
                employeeStage.copyTo(employeeFile, overwrite = true)
                payrollStage.copyTo(payrollFile, overwrite = true)
                DatabaseEncryption.installPassphrase(app, EMPLOYEE_DB, employeePassphrase)
                DatabaseEncryption.installPassphrase(app, PAYROLL_DB, payrollPassphrase)
                validateEncryptedDatabase(employeeFile, employeePassphrase)
                validateEncryptedDatabase(payrollFile, payrollPassphrase)
            } catch (error: Exception) {
                employeeRollback.takeIf { it.exists() }?.copyTo(employeeFile, overwrite = true)
                payrollRollback.takeIf { it.exists() }?.copyTo(payrollFile, overwrite = true)
                runCatching { DatabaseEncryption.installPassphrase(app, EMPLOYEE_DB, oldEmployeePassphrase) }
                runCatching { DatabaseEncryption.installPassphrase(app, PAYROLL_DB, oldPayrollPassphrase) }
                deleteSidecars(employeeFile)
                deleteSidecars(payrollFile)
                throw IllegalStateException("فشلت الاستعادة؛ تمت محاولة إعادة البيانات السابقة دون حذف النسخة الاحتياطية", error)
            } finally {
                reopenDatabases(app)
            }
        } finally {
            staging.deleteRecursively()
            plainZip.fill(0)
        }
    }

    private fun readAndValidateArchive(zipBytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                require(!entry.isDirectory && entry.name in ALLOWED_ENTRIES) { "تحتوي النسخة الاحتياطية على ملف غير مسموح" }
                require(entry.name !in entries) { "توجد ملفات مكررة داخل النسخة الاحتياطية" }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var entryBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    entryBytes += count
                    totalBytes += count
                    require(entryBytes <= MAX_ENTRY_BYTES && totalBytes <= MAX_BACKUP_BYTES) {
                        "حجم محتويات النسخة الاحتياطية يتجاوز الحد المسموح"
                    }
                    output.write(buffer, 0, count)
                }
                entries[entry.name] = output.toByteArray()
                input.closeEntry()
            }
        }
        require(entries.keys == ALLOWED_ENTRIES) { "النسخة الاحتياطية ناقصة أو غير متوافقة" }
        require(entries.getValue("format.txt").toString(StandardCharsets.UTF_8) == "GOLDEN360_BACKUP_V1") {
            "إصدار النسخة الاحتياطية غير مدعوم"
        }
        return entries
    }

    private fun validateEncryptedDatabase(file: File, passphrase: String) {
        check(file.isFile && file.length() > 0L) { "ملف قاعدة البيانات فارغ أو غير موجود" }
        SQLiteDatabase.openDatabase(
            file.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "فشل فحص سلامة إحدى قواعد البيانات"
                }
            }
            database.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                check(cursor.moveToFirst()) { "تعذر قراءة قاعدة البيانات" }
            }
        }
    }

    private fun closeDatabases() {
        EmployeeDatabase.closeForBackup()
        PayrollDatabase.closeForBackup()
    }

    private fun reopenDatabases(context: Context) {
        runCatching { EmployeeDatabase.get(context) }
        runCatching { PayrollDatabase.get(context) }
    }

    private fun addEntry(output: ZipOutputStream, name: String, bytes: ByteArray) {
        output.putNextEntry(ZipEntry(name))
        output.write(bytes)
        output.closeEntry()
    }

    private fun deleteSidecars(database: File) {
        File(database.parentFile, database.name + "-wal").delete()
        File(database.parentFile, database.name + "-shm").delete()
        File(database.parentFile, database.name + "-journal").delete()
    }
}
