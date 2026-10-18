package com.golden360.app.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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
        // Open both databases through their normal initialization path first. This also
        // completes the supported plaintext-to-SQLCipher migration before copying either file.
        EmployeeDatabase.get(app)
        PayrollDatabase.get(app)
        val employeePassphrase = DatabaseEncryption.currentPassphrase(app, EMPLOYEE_DB)
        val payrollPassphrase = DatabaseEncryption.currentPassphrase(app, PAYROLL_DB)
        closeDatabases()
        var zipFile: File? = null
        var encryptedFile: File? = null
        try {
            val employeeFile = app.getDatabasePath(EMPLOYEE_DB)
            val payrollFile = app.getDatabasePath(PAYROLL_DB)
            check(employeeFile.isFile && employeeFile.length() > 0L) { "قاعدة بيانات الموظفين غير موجودة" }
            check(payrollFile.isFile && payrollFile.length() > 0L) { "قاعدة بيانات الرواتب غير موجودة" }
            validateBackupSizes(employeeFile.length(), payrollFile.length())

            // Build the ZIP on disk and stream each database file into it. Do not keep both
            // databases plus a full ZIP copy in heap memory on low-memory Android devices.
            val archive = File.createTempFile("golden360-backup-", ".zip", app.cacheDir)
            zipFile = archive
            ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { out ->
                addEntry(out, "format.txt", "GOLDEN360_BACKUP_V1".toByteArray(StandardCharsets.UTF_8))
                addEntry(out, "employees.db", FileInputStream(employeeFile))
                addEntry(out, "employees.key", employeePassphrase.toByteArray(StandardCharsets.UTF_8))
                addEntry(out, "payroll.db", FileInputStream(payrollFile))
                addEntry(out, "payroll.key", payrollPassphrase.toByteArray(StandardCharsets.UTF_8))
            }
            validateBackupSizes(employeeFile.length(), payrollFile.length(), archive.length())

            // Encrypt directly from the temporary ZIP into a second temporary file. Only the
            // final encrypted payload is returned to the Android share/document flow.
            val encrypted = File.createTempFile("golden360-backup-", ".g360", app.cacheDir)
            encryptedFile = encrypted
            FileInputStream(archive).use { input ->
                BufferedOutputStream(FileOutputStream(encrypted)).use { output ->
                    BackupCrypto.encrypt(input, output, password)
                }
            }
            validateBackupSizes(employeeFile.length(), payrollFile.length(), archive.length(), encrypted.length())
            return encrypted.readBytes()
        } finally {
            zipFile?.delete()
            encryptedFile?.delete()
            reopenDatabases(app)
        }
    }

    fun restoreEncryptedBackup(context: Context, encryptedBackup: ByteArray, password: CharArray) {
        require(encryptedBackup.size <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح" }
        val app = context.applicationContext
        val staging = File.createTempFile("golden360-restore-", ".tmp", app.cacheDir).apply {
            check(delete() && mkdirs()) { "تعذر تجهيز الاستعادة الآمنة" }
        }
        var plainZipFile: File? = null
        try {
            // Decrypt to a temporary file rather than allocating the complete decrypted archive.
            val archive = File.createTempFile("golden360-restore-", ".zip", app.cacheDir)
            plainZipFile = archive
            ByteArrayInputStream(encryptedBackup).use { input ->
                BufferedOutputStream(FileOutputStream(archive)).use { output ->
                    BackupCrypto.decrypt(input, output, password)
                }
            }
            require(archive.length() in 1..MAX_BACKUP_BYTES.toLong()) {
                "محتوى النسخة الاحتياطية أكبر من الحد المسموح أو غير صالح"
            }
            val entries = readAndValidateArchive(archive, staging)
            val employeePassphrase = entries.getValue("employees.key").readText(StandardCharsets.UTF_8)
            val payrollPassphrase = entries.getValue("payroll.key").readText(StandardCharsets.UTF_8)
            require(employeePassphrase.isNotBlank() && payrollPassphrase.isNotBlank()) { "مفاتيح قواعد البيانات غير صالحة" }

            val employeeStage = entries.getValue("employees.db")
            val payrollStage = entries.getValue("payroll.db")
            validateEncryptedDatabase(employeeStage, employeePassphrase)
            validateEncryptedDatabase(payrollStage, payrollPassphrase)

            val employeeFile = app.getDatabasePath(EMPLOYEE_DB)
            val payrollFile = app.getDatabasePath(PAYROLL_DB)
            employeeFile.parentFile?.let { check(it.exists() || it.mkdirs()) { "تعذر الوصول إلى مجلد البيانات" } }
            val employeeRollback = File(staging, "employees.previous")
            val payrollRollback = File(staging, "payroll.previous")
            val hadEmployeeFile = employeeFile.exists()
            val hadPayrollFile = payrollFile.exists()
            val oldEmployeePassphrase = runCatching { DatabaseEncryption.currentPassphrase(app, EMPLOYEE_DB) }.getOrNull()
            val oldPayrollPassphrase = runCatching { DatabaseEncryption.currentPassphrase(app, PAYROLL_DB) }.getOrNull()

            // Close Room before taking rollback copies so committed WAL contents are checkpointed
            // into the database files. Copying the main DB while it is open can make rollback stale.
            closeDatabases()
            try {
                if (hadEmployeeFile) check(employeeFile.copyTo(employeeRollback, overwrite = true).exists())
                if (hadPayrollFile) check(payrollFile.copyTo(payrollRollback, overwrite = true).exists())
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
                    if (hadEmployeeFile && employeeRollback.exists()) employeeRollback.copyTo(employeeFile, overwrite = true)
                    else employeeFile.delete()
                    if (hadPayrollFile && payrollRollback.exists()) payrollRollback.copyTo(payrollFile, overwrite = true)
                    else payrollFile.delete()
                    if (oldEmployeePassphrase != null) {
                        runCatching { DatabaseEncryption.installPassphrase(app, EMPLOYEE_DB, oldEmployeePassphrase) }
                    } else {
                        runCatching { DatabaseEncryption.clearPassphrase(app, EMPLOYEE_DB) }
                    }
                    if (oldPayrollPassphrase != null) {
                        runCatching { DatabaseEncryption.installPassphrase(app, PAYROLL_DB, oldPayrollPassphrase) }
                    } else {
                        runCatching { DatabaseEncryption.clearPassphrase(app, PAYROLL_DB) }
                    }
                    deleteSidecars(employeeFile)
                    deleteSidecars(payrollFile)
                    throw IllegalStateException("فشلت الاستعادة؛ تمت محاولة إعادة البيانات السابقة دون حذف النسخة الاحتياطية", error)
                }
            } finally {
                reopenDatabases(app)
            }
        } finally {
            plainZipFile?.delete()
            staging.deleteRecursively()
        }
    }

    internal fun validateBackupSizes(
        employeeBytes: Long,
        payrollBytes: Long,
        zipBytes: Long? = null,
        encryptedBytes: Long? = null
    ) {
        require(employeeBytes in 1..MAX_ENTRY_BYTES.toLong()) { "حجم قاعدة بيانات الموظفين يتجاوز الحد المسموح" }
        require(payrollBytes in 1..MAX_ENTRY_BYTES.toLong()) { "حجم قاعدة بيانات الرواتب يتجاوز الحد المسموح" }
        if (zipBytes != null) require(zipBytes in 1..MAX_BACKUP_BYTES.toLong()) {
            "حجم النسخة الاحتياطية أكبر من الحد المسموح"
        }
        if (encryptedBytes != null) require(encryptedBytes in 1..MAX_BACKUP_BYTES.toLong()) {
            "حجم ملف النسخة الاحتياطية المشفر أكبر من الحد المسموح"
        }
    }

    private fun readAndValidateArchive(zipFile: File, staging: File): Map<String, File> {
        val entries = linkedMapOf<String, File>()
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                require(!entry.isDirectory && entry.name in ALLOWED_ENTRIES) {
                    "تحتوي النسخة الاحتياطية على ملف غير مسموح"
                }
                require(entry.name !in entries) { "توجد ملفات مكررة داخل النسخة الاحتياطية" }
                val target = File(staging, entry.name)
                val entryLimit = if (entry.name == "employees.db" || entry.name == "payroll.db") {
                    MAX_ENTRY_BYTES.toLong()
                } else {
                    4096L
                }
                var entryBytes = 0L
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        entryBytes += count
                        totalBytes += count
                        require(entryBytes <= entryLimit && totalBytes <= MAX_BACKUP_BYTES) {
                            "حجم محتويات النسخة الاحتياطية يتجاوز الحد المسموح"
                        }
                        output.write(buffer, 0, count)
                    }
                }
                entries[entry.name] = target
                input.closeEntry()
            }
        }
        require(entries.keys == ALLOWED_ENTRIES) { "النسخة الاحتياطية ناقصة أو غير متوافقة" }
        require(entries.getValue("format.txt").readText(StandardCharsets.UTF_8) == "GOLDEN360_BACKUP_V1") {
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
        addEntry(output, name, ByteArrayInputStream(bytes))
    }

    private fun addEntry(output: ZipOutputStream, name: String, input: java.io.InputStream) {
        output.putNextEntry(ZipEntry(name))
        input.use { it.copyTo(output, 8192) }
        output.closeEntry()
    }

    private fun deleteSidecars(database: File) {
        File(database.parentFile, database.name + "-wal").delete()
        File(database.parentFile, database.name + "-shm").delete()
        File(database.parentFile, database.name + "-journal").delete()
    }
}
