package dev.expensetracker.prototype.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class StorageFailure { KEY_RECOVERY_REQUIRED, STORAGE_UNAVAILABLE, DATABASE_OPEN_FAILED }

/** Bounded reason only: no paths, account subjects, SQL, or key material in exception messages. */
class EncryptedStorageException(val reason: StorageFailure) : IllegalStateException(reason.name)

/**
 * One random database secret per authenticated subject, wrapped with an install-local Keystore key.
 * No user-authentication requirement on wrapping: background capture is separate from the UI lock.
 */
class DatabaseKeyVault(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, "encrypted_ledgers")

    fun databaseFile(subject: String): File = File(directory, "${subjectName(subject)}.db")

    internal fun secret(subject: String): ByteArray = synchronized(lock) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw EncryptedStorageException(StorageFailure.STORAGE_UNAVAILABLE)
            }
            val name = subjectName(subject)
            val database = File(directory, "$name.db")
            val wrapped = File(directory, "$name.key")
            val atomic = AtomicFile(wrapped)
            val hasWrapped = wrapped.exists() || File(wrapped.path + ".bak").exists()
            // Sidecars count as existing data too; never replace their missing key silently.
            val hasDatabase = listOf("", "-wal", "-shm", "-journal").any { File(database.path + it).exists() }
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = if (store.containsAlias(WRAPPING_ALIAS)) {
                (store.getKey(WRAPPING_ALIAS, null) as? SecretKey)
                    ?: throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
            } else {
                if (hasWrapped || hasDatabase) throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(
                        WRAPPING_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false).build())
                }.generateKey()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(subject.toByteArray(Charsets.UTF_8))
            if (hasWrapped) {
                val bytes = atomic.openRead().use { input ->
                    val buffer = ByteArray(SecretEnvelope.ENVELOPE_BYTES + 1)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    buffer.copyOf(count)
                }
                try {
                    SecretEnvelope.open(key, bytes, digest)
                } catch (_: GeneralSecurityException) {
                    throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
                } catch (_: IllegalArgumentException) {
                    throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
                }
            } else {
                if (hasDatabase) throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
                val secret = ByteArray(SecretEnvelope.SECRET_BYTES).also { SecureRandom().nextBytes(it) }
                try {
                    val envelope = SecretEnvelope.seal(key, secret, digest)
                    val output = atomic.startWrite()
                    try {
                        output.write(envelope)
                        atomic.finishWrite(output)
                    } catch (failure: IOException) {
                        atomic.failWrite(output)
                        throw failure
                    }
                    secret
                } catch (failure: Exception) {
                    secret.fill(0)
                    throw failure
                }
            }
        } catch (failure: EncryptedStorageException) {
            throw failure
        } catch (_: GeneralSecurityException) {
            throw EncryptedStorageException(StorageFailure.KEY_RECOVERY_REQUIRED)
        } catch (_: IOException) {
            throw EncryptedStorageException(StorageFailure.STORAGE_UNAVAILABLE)
        }
    }

    private fun subjectName(subject: String): String {
        require(subject.isNotBlank() && subject == subject.trim()) { "INVALID_AUTH_SUBJECT" }
        return MessageDigest.getInstance("SHA-256").digest(subject.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val WRAPPING_ALIAS = "expense-tracker.db-wrap.v1"
        val lock = Any()
    }
}
