package dev.expensetracker.prototype.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only generated secrets and synthetic subjects; never touches the phone's messages. */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyVaultDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withSubject(test: (String, DatabaseKeyVault, File) -> Unit) {
        val subject = "synthetic-storage-test-${UUID.randomUUID()}"
        val vault = DatabaseKeyVault(context)
        val database = vault.databaseFile(subject)
        try {
            test(subject, vault, database)
        } finally {
            listOf(database, File(database.path + "-wal"), File(database.path + "-shm"), File(database.path + "-journal"),
                File(database.path.removeSuffix(".db") + ".key"),
                File(database.path.removeSuffix(".db") + ".key.bak"),
                File(database.path.removeSuffix(".db") + ".key.new"),
            ).forEach { it.delete() }
        }
    }

    @Test fun wrappedSecretSurvivesVaultRecreationAndLivesOutsideBackup() = withSubject { subject, vault, file ->
        val first = vault.secret(subject)
        val reopened = DatabaseKeyVault(context).secret(subject)
        try {
            assertEquals(32, first.size)
            assertArrayEquals(first, reopened)
            assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
            assertFalse(file.name.contains(subject))
            val stored = File(file.path.removeSuffix(".db") + ".key").readBytes()
            assertEquals(SecretEnvelope.ENVELOPE_BYTES, stored.size)
            assertFalse(stored.contentEquals(first))
        } finally {
            first.fill(0)
            reopened.fill(0)
        }
    }

    @Test fun existingDatabaseWithoutWrappedSecretRequiresRecovery() = withSubject { subject, vault, file ->
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3)) // Synthetic sentinel, not a ledger.
        assertEquals(StorageFailure.KEY_RECOVERY_REQUIRED, assertThrows(EncryptedStorageException::class.java) {
            vault.secret(subject)
        }.reason)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test fun tamperedWrappedSecretRequiresRecoveryWithoutOverwritingIt() = withSubject { subject, vault, file ->
        vault.secret(subject).fill(0)
        val wrapped = File(file.path.removeSuffix(".db") + ".key")
        val damaged = wrapped.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        wrapped.writeBytes(damaged)
        assertEquals(StorageFailure.KEY_RECOVERY_REQUIRED, assertThrows(EncryptedStorageException::class.java) {
            vault.secret(subject)
        }.reason)
        assertArrayEquals(damaged, wrapped.readBytes())
    }

    @Test fun orphanedJournalWithoutWrappedSecretRequiresRecovery() = withSubject { subject, vault, file ->
        file.parentFile!!.mkdirs()
        val journal = File(file.path + "-journal")
        journal.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(StorageFailure.KEY_RECOVERY_REQUIRED, assertThrows(EncryptedStorageException::class.java) {
            vault.secret(subject)
        }.reason)
        assertFalse(file.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), journal.readBytes())
    }

    @Test fun differentSubjectsHaveDifferentSecrets() = withSubject { firstSubject, firstVault, _ ->
        withSubject { secondSubject, secondVault, _ ->
            val first = firstVault.secret(firstSubject)
            val second = secondVault.secret(secondSubject)
            try {
                assertFalse(first.contentEquals(second))
            } finally {
                first.fill(0)
                second.fill(0)
            }
        }
    }
}
