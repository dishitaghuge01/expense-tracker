package dev.expensetracker.prototype.data

import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

/**
 * SQLCipher's standard Room adapter passes a null corruption handler, whose default
 * can delete a database. Keep the same SQLCipher/Room API but explicitly refuse deletion.
 */
internal class PreservingCipherFactory(private val secret: ByteArray) : SupportSQLiteOpenHelper.Factory {
    init { require(secret.size == SecretEnvelope.SECRET_BYTES) { "INVALID_SECRET_SIZE" } }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val preserveFile = DatabaseErrorHandler { database, _ ->
            try {
                database.close()
            } finally {
                throw SQLiteException("DATABASE_OPEN_FAILED")
            }
        }
        return object : SQLiteOpenHelper(
            configuration.context, configuration.name, secret, null, configuration.callback.version,
            0, preserveFile, null, false,
        ) {
            override fun onCreate(db: SQLiteDatabase) = configuration.callback.onCreate(db)
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                configuration.callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                configuration.callback.onDowngrade(db, oldVersion, newVersion)
            override fun onConfigure(db: SQLiteDatabase) = configuration.callback.onConfigure(db)
            override fun onOpen(db: SQLiteDatabase) = configuration.callback.onOpen(db)
        }
    }
}
