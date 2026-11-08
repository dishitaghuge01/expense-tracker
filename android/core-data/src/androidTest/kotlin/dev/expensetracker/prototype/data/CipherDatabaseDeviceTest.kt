package dev.expensetracker.prototype.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** SQLCipher/adapter checks on generated data only; not a completed Room schema/migration test. */
@RunWith(AndroidJUnit4::class)
class CipherDatabaseDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun helper(file: File, secret: ByteArray): SupportSQLiteOpenHelper {
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE synthetic (id INTEGER NOT NULL PRIMARY KEY)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("UNEXPECTED_TEST_MIGRATION")
            }
        }
        return PreservingCipherFactory(secret).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(file.absolutePath).callback(callback).build(),
        )
    }

    private fun withDatabase(test: (File, ByteArray) -> Unit) {
        val file = File(context.noBackupFilesDir, "synthetic-cipher-test-${UUID.randomUUID()}.db")
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            test(file, secret)
        } finally {
            secret.fill(0)
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }
    }

    @Test fun encryptedFileReopensWithSameSecretAndRejectsWrongSecretWithoutDeletingData() = withDatabase { file, secret ->
        helper(file, secret).use { it.writableDatabase.execSQL("INSERT INTO synthetic (id) VALUES (1)") }
        val before = file.readBytes()
        assertFalse(before.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
        helper(file, secret).use { opened ->
            opened.readableDatabase.query("SELECT COUNT(*) FROM synthetic").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
        val wrong = secret.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        try {
            helper(file, wrong).use { opened ->
                assertThrows(RuntimeException::class.java) { opened.writableDatabase }
            }
        } finally {
            wrong.fill(0)
        }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun corruptFileIsNotDeletedOrReplacedAutomatically() = withDatabase { file, secret ->
        val damaged = ByteArray(4096) { 0x7f }
        file.writeBytes(damaged)
        helper(file, secret).use { opened ->
            assertThrows(RuntimeException::class.java) { opened.writableDatabase }
        }
        assertArrayEquals(damaged, file.readBytes())
    }
}
