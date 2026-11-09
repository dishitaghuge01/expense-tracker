package dev.expensetracker.prototype.data

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

/** Owns the factory's secret until Room closes; do not clear a lazily used factory password early. */
class EncryptedDatabaseHandle<T : RoomDatabase> internal constructor(
    val database: T,
    private val secret: ByteArray,
) : AutoCloseable {
    override fun close() {
        try {
            database.close()
        } finally {
            secret.fill(0)
        }
    }
}

/** Storage infrastructure only. No Room schema/DAO or authenticated ledger is opened by the prototype. */
class EncryptedRoomFactory(context: Context) {
    private val application = context.applicationContext
    private val vault = DatabaseKeyVault(application)

    @WorkerThread
    fun <T : RoomDatabase> open(
        authenticatedSubject: String,
        databaseClass: Class<T>,
        vararg migrations: Migration,
        callbacks: List<RoomDatabase.Callback> = emptyList(),
    ): EncryptedDatabaseHandle<T> {
        try {
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())
        } catch (_: LinkageError) {
            throw EncryptedStorageException(StorageFailure.STORAGE_UNAVAILABLE)
        }
        val secret = vault.secret(authenticatedSubject)
        var database: T? = null
        try {
            database = Room.databaseBuilder(application, databaseClass, vault.databaseFile(authenticatedSubject).absolutePath)
                .openHelperFactory(PreservingCipherFactory(secret))
                .addMigrations(*migrations)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // SQLCipher returns a status row; only 'off' disables the log destination.
                        db.query("PRAGMA cipher_log = 'off'").use { cursor ->
                            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                                "CIPHER_LOG_CONFIGURATION_FAILED"
                            }
                        }
                    }
                })
                .also { builder -> callbacks.forEach { builder.addCallback(it) } }
                .build()
            // Force key/schema verification now, rather than return an apparently working lazy handle.
            database.openHelper.writableDatabase
            return EncryptedDatabaseHandle(database, secret)
        } catch (_: RuntimeException) {
            try {
                database?.close()
            } catch (_: RuntimeException) {
                // Keep the public failure bounded even if cleanup also fails.
            } finally {
                secret.fill(0)
            }
            throw EncryptedStorageException(StorageFailure.DATABASE_OPEN_FAILED)
        }
    }
}
