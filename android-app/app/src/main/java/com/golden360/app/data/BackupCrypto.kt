package com.golden360.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based authenticated encryption for portable backup files.
 * Format: magic(8) + salt(16) + IV(12) + AES-GCM ciphertext.
 */
internal object BackupCrypto {
    private val MAGIC = byteArrayOf(0x47, 0x33, 0x36, 0x30, 0x42, 0x4b, 0x50, 0x31) // G360BKP1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000
    private const val TAG_BITS = 128
    private const val MIN_PASSWORD_LENGTH = 10

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "كلمة المرور يجب ألا تقل عن 10 أحرف" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = MAGIC + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        val encrypted = cipher.doFinal(plain)
        return header + encrypted
    }

    fun decrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "كلمة المرور يجب ألا تقل عن 10 أحرف" }
        require(payload.size > MAGIC.size + SALT_BYTES + IV_BYTES + 16) { "ملف النسخة الاحتياطية غير صالح أو تالف" }
        val headerLength = MAGIC.size + SALT_BYTES + IV_BYTES
        val header = payload.copyOfRange(0, headerLength)
        require(header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "الملف ليس نسخة احتياطية من GOLDEN 360" }
        val salt = payload.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = payload.copyOfRange(MAGIC.size + SALT_BYTES, headerLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(payload.copyOfRange(headerLength, payload.size))
        } catch (error: AEADBadTagException) {
            throw SecurityException("كلمة المرور غير صحيحة أو تم تعديل ملف النسخة الاحتياطية", error)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES").also { encoded.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
