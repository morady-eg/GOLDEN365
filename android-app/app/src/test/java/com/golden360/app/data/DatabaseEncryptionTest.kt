package com.golden360.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseEncryptionTest {
    @Test
    fun recognizesLegacyPlaintextSqliteHeader() {
        val file = File.createTempFile("golden360-plaintext", ".db")
        try {
            file.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(32))
            assertTrue(DatabaseEncryption.isPlaintextSqliteDatabase(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun doesNotTreatEncryptedOrUnknownFileAsPlaintextSqlite() {
        val file = File.createTempFile("golden360-encrypted", ".db")
        try {
            file.writeBytes(ByteArray(64) { (it + 1).toByte() })
            assertFalse(DatabaseEncryption.isPlaintextSqliteDatabase(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsShortFilesAsPlaintextSqlite() {
        val file = File.createTempFile("golden360-short", ".db")
        try {
            file.writeBytes("SQLite".toByteArray(Charsets.US_ASCII))
            assertFalse(DatabaseEncryption.isPlaintextSqliteDatabase(file))
        } finally {
            file.delete()
        }
    }
}
