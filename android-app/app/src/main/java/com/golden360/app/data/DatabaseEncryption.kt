package com.golden360.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts Room databases with SQLCipher. Existing plaintext databases are exported to an
 * encrypted temporary database and atomically replaced only after the encrypted copy is verified.
 * A per-database random passphrase is wrapped by an Android Keystore AES-GCM key.
 */
internal object DatabaseEncryption {
    private const val PREFERENCES = "golden360_encryption_keys"
    private const val KEY_ALIAS = "golden360_database_wrapping_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val KEY_SIZE_BITS = 256

    @Synchronized
    fun prepareEncryptedDatabase(context: Context, databaseName: String): ByteArray {
        SQLiteDatabase.loadLibs(context)
        val passphrase = getOrCreatePassphrase(context, databaseName)
        migratePlaintextDatabaseIfNeeded(context.getDatabasePath(databaseName), passphrase)
        return passphrase.toByteArray(StandardCharsets.UTF_8)
    }

    private fun getOrCreatePassphrase(context: Context, databaseName: String): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val preferenceName = "wrapped.$databaseName"
        val existing = preferences.getString(preferenceName, null)
        if (existing != null) return unwrapPassphrase(databaseName, existing)

        val random = ByteArray(32).also(SecureRandom()::nextBytes)
        val passphrase = Base64.encodeToString(random, Base64.NO_WRAP)
        random.fill(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        cipher.updateAAD(databaseName.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))
        val wrapped = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(preferenceName, wrapped).commit()) {
            "تعذر حفظ مفتاح قاعدة البيانات بأمان"
        }
        return passphrase
    }

    private fun unwrapPassphrase(databaseName: String, wrapped: String): String {
        val parts = wrapped.split(":", limit = 2)
        require(parts.size == 2) { "بيانات مفتاح قاعدة البيانات تالفة" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(databaseName.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("مفتاح تشفير قاعدة البيانات غير موجود؛ أوقفنا الفتح لحماية البيانات")
    }

    private fun migratePlaintextDatabaseIfNeeded(database: File, passphrase: String) {
        val parent = requireNotNull(database.parentFile)
        val temporary = File(parent, database.name + ".encrypted-migration")
        val backup = File(parent, database.name + ".plaintext-backup")

        if (!database.exists() && temporary.exists() && canOpenEncrypted(temporary, passphrase)) {
            check(temporary.renameTo(database)) { "تعذر استكمال ترحيل قاعدة البيانات المشفرة" }
            backup.delete()
            return
        }
        if (!database.exists() && backup.exists()) {
            check(backup.renameTo(database)) { "تعذر استعادة قاعدة البيانات السابقة" }
        }
        if (!database.exists()) {
            temporary.delete()
            return
        }
        if (canOpenEncrypted(database, passphrase)) {
            temporary.delete()
            backup.delete()
            deleteSidecars(database)
            return
        }

        temporary.delete()
        val source = try {
            SQLiteDatabase.openDatabase(database.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE)
        } catch (error: Exception) {
            throw IllegalStateException(
                "تعذر فتح قاعدة البيانات الحالية أو التحقق من مفتاح تشفيرها؛ لم يتم حذف البيانات",
                error
            )
        }
        try {
            val escapedPath = temporary.absolutePath.replace("'", "''")
            source.execSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY '$passphrase'")
            try {
                source.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { cursor ->
                    check(cursor.moveToFirst()) { "فشل تصدير قاعدة البيانات إلى النسخة المشفرة" }
                }
            } finally {
                source.execSQL("DETACH DATABASE encrypted")
            }
        } finally {
            source.close()
        }

        check(canOpenEncrypted(temporary, passphrase)) {
            "تعذر التحقق من قاعدة البيانات المشفرة؛ تم الاحتفاظ بالنسخة الأصلية"
        }
        backup.delete()
        check(database.renameTo(backup)) { "تعذر حفظ النسخة الأصلية أثناء التشفير" }
        if (!temporary.renameTo(database)) {
            backup.renameTo(database)
            error("تعذر استبدال قاعدة البيانات بالنسخة المشفرة")
        }
        if (!canOpenEncrypted(database, passphrase)) {
            database.delete()
            backup.renameTo(database)
            error("فشل التحقق النهائي من التشفير؛ تمت استعادة النسخة الأصلية")
        }
        backup.delete()
        temporary.delete()
        deleteSidecars(database)
    }

    private fun canOpenEncrypted(file: File, passphrase: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                    cursor.moveToFirst()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteSidecars(database: File) {
        File(database.parentFile, database.name + "-wal").delete()
        File(database.parentFile, database.name + "-shm").delete()
        File(database.parentFile, database.name + "-journal").delete()
    }
}
