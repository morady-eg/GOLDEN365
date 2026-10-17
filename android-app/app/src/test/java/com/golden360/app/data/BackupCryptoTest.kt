package com.golden360.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun encryptedBackupCanBeDecryptedWithCorrectPassword() {
        val source = "GOLDEN360 test database payload".toByteArray()
        val encrypted = BackupCrypto.encrypt(source, "CorrectHorseBatteryStaple!".toCharArray())
        assertArrayEquals(source, BackupCrypto.decrypt(encrypted, "CorrectHorseBatteryStaple!".toCharArray()))
    }

    @Test
    fun encryptingSamePayloadProducesDifferentCiphertext() {
        val source = "same payload".toByteArray()
        val password = "CorrectHorseBatteryStaple!".toCharArray()
        assertNotEquals(
            BackupCrypto.encrypt(source, password).toList(),
            BackupCrypto.encrypt(source, password).toList()
        )
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = BackupCrypto.encrypt("private data".toByteArray(), "CorrectHorseBatteryStaple!".toCharArray())
        assertThrows(SecurityException::class.java) {
            BackupCrypto.decrypt(encrypted, "IncorrectPassword!".toCharArray())
        }
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val encrypted = BackupCrypto.encrypt("private data".toByteArray(), "CorrectHorseBatteryStaple!".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(SecurityException::class.java) {
            BackupCrypto.decrypt(encrypted, "CorrectHorseBatteryStaple!".toCharArray())
        }
    }

    @Test
    fun backupSizeValidationRejectsDatabasesThatRestoreWouldRefuse() {
        BackupManager.validateBackupSizes(100L * 1024 * 1024, 1024L)
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.validateBackupSizes(100L * 1024 * 1024 + 1, 1024L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.validateBackupSizes(1024L, 1024L, 200L * 1024 * 1024 + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.validateBackupSizes(1024L, 1024L, 100L, 200L * 1024 * 1024 + 1)
        }
    }

    @Test
    fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(byteArrayOf(1), "short".toCharArray())
        }
    }
}
