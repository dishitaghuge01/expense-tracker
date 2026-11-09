package dev.expensetracker.prototype.data.ledger

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.expensetracker.prototype.data.DatabaseKeyVault
import dev.expensetracker.prototype.data.EncryptedRoomFactory
import dev.expensetracker.prototype.data.EncryptedStorageException
import dev.expensetracker.prototype.data.PreservingCipherFactory
import java.io.File
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic-only isolation of native SQLCipher, generated Room, and application callbacks.
 * Reuses the existing generated v3 schema: no new schema exports or test KSP setup are required.
 * Failures deliberately discard causes/messages; these helpers must never open an app/user DB.
 */
@RunWith(AndroidJUnit4::class)
class RoomOpenIsolationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun <T> at(stage: String, action: () -> T): T = try {
        action()
    } catch (failure: RuntimeException) {
        throw AssertionError("SYNTHETIC_ROOM_OPEN stage=$stage category=${safeCategory(failure)}")
    } catch (_: LinkageError) {
        throw AssertionError("SYNTHETIC_ROOM_OPEN stage=$stage category=NATIVE_LINKAGE")
    }

    // Inspect messages only in memory and only for our newly generated synthetic fixtures.
    // Never attach the original throwable or emit its class name, SQL, path, or message.
    private fun safeCategory(failure: RuntimeException): String {
        var current: Throwable? = failure
        repeat(8) {
            val cause = current ?: return@repeat
            if (cause is EncryptedStorageException) return cause.reason.name
            val message = cause.message.orEmpty().lowercase(Locale.ROOT)
            if ("queries can be performed" in message || "query or rawquery" in message) {
                return "ROW_RETURNING_STATEMENT"
            }
            if ("syntax error" in message) return "SQL_SYNTAX"
            if ("foreign key constraint" in message) return "FOREIGN_KEY_CONSTRAINT"
            if ("no such table" in message) return "MISSING_TABLE"
            if ("no such column" in message) return "MISSING_COLUMN"
            if ("cannot perform this operation" in message && "transaction" in message) {
                return "TRANSACTION_STATE"
            }
            current = cause.cause
        }
        return if (failure is IllegalStateException) "ILLEGAL_STATE" else "RUNTIME_OTHER"
    }

    private fun initializeCipher() = at("NATIVE_INITIALIZATION") {
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
    }

    private fun deleteSyntheticFiles(file: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private fun withSyntheticSecret(test: (File, ByteArray) -> Unit) {
        val file = File(context.noBackupFilesDir, "synthetic-room-open-${UUID.randomUUID()}.db")
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            initializeCipher()
            test(file, secret)
        } finally {
            secret.fill(0)
            deleteSyntheticFiles(file)
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use {
        assertTrue("SYNTHETIC_QUERY_MISSING_ROW", it.moveToFirst())
        it.getInt(0)
    }

    private fun assertForeignKeys(db: SupportSQLiteDatabase) {
        assertEquals(1, scalar(db, "PRAGMA foreign_keys"))
    }

    private fun insertCategory(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO personal_category (id, name) VALUES ('synthetic-category', 'Synthetic Category')")
    }

    private fun assertCategory(db: SupportSQLiteDatabase) {
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM personal_category WHERE id = 'synthetic-category'"))
    }

    private fun assertCategoryConstraint(db: SupportSQLiteDatabase) {
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name = 'category_active_name_insert'"))
        assertThrows(RuntimeException::class.java) {
            db.execSQL("INSERT INTO personal_category (id, name) VALUES ('synthetic-duplicate', 'SYNTHETIC CATEGORY')")
        }
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM personal_category"))
    }

    @Test fun nativeAdapterDisablesCipherLogThroughStatusQueryOnCreateAndReopen() = withSyntheticSecret { file, secret ->
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE synthetic (id INTEGER NOT NULL PRIMARY KEY)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("UNEXPECTED_SYNTHETIC_MIGRATION")
            }
            override fun onOpen(db: SupportSQLiteDatabase) = at("NATIVE_QUERY_CALLBACK") {
                db.execSQL("PRAGMA foreign_keys = ON")
                // SQLCipher 4.20 returns a status row; 'off' disables the destination.
                db.query("PRAGMA cipher_log = 'off'").use {
                    assertTrue("SYNTHETIC_CIPHER_LOG_MISSING_STATUS", it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                    assertFalse(it.moveToNext())
                }
            }
        }
        repeat(2) { pass ->
            at(if (pass == 0) "NATIVE_QUERY_CREATE" else "NATIVE_QUERY_REOPEN") {
                PreservingCipherFactory(secret).create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(file.absolutePath).callback(callback).build(),
                ).use { helper ->
                    val db = helper.writableDatabase
                    assertForeignKeys(db)
                    db.query("PRAGMA cipher_version").use {
                        assertTrue(it.moveToFirst())
                        assertFalse(it.getString(0).isNullOrBlank())
                    }
                    if (pass == 0) db.execSQL("INSERT INTO synthetic (id) VALUES (1)")
                    assertEquals(1, scalar(db, "SELECT COUNT(*) FROM synthetic"))
                }
            }
        }
    }

    private fun roomWithoutFactoryCallbacks(installConstraints: Boolean) = withSyntheticSecret { file, secret ->
        repeat(2) { pass ->
            val layer = if (installConstraints) "ROOM_LEDGER_CALLBACKS" else "ROOM_NO_APP_CALLBACKS"
            at("${layer}_${if (pass == 0) "CREATE" else "REOPEN"}") {
                val builder = Room.databaseBuilder(context, LocalLedgerDatabase::class.java, file.absolutePath)
                    .openHelperFactory(PreservingCipherFactory(secret))
                if (installConstraints) builder.addCallback(LedgerConstraints)
                val room = builder.build()
                try {
                    // Force the same lazy opening boundary as EncryptedRoomFactory, but retain safe diagnostics.
                    val db = room.openHelper.writableDatabase
                    assertEquals(3, db.version)
                    assertForeignKeys(db)
                    if (pass == 0) insertCategory(db)
                    assertCategory(db)
                    if (installConstraints) assertCategoryConstraint(db)
                } finally {
                    room.close()
                }
            }
        }
    }

    @Test fun generatedRoomAndPreservingAdapterOpenWithoutApplicationCallbacks() =
        roomWithoutFactoryCallbacks(installConstraints = false)

    @Test fun ledgerCallbacksOpenWithoutFactoryPrivacyCallback() =
        roomWithoutFactoryCallbacks(installConstraints = true)

    private fun productionFactory(installConstraints: Boolean) {
        val subject = "synthetic-room-open-factory-${UUID.randomUUID()}"
        val file = DatabaseKeyVault(context).databaseFile(subject)
        try {
            repeat(2) { pass ->
                val layer = if (installConstraints) "PRODUCTION_LEDGER" else "PRODUCTION_FACTORY_ONLY"
                at("${layer}_${if (pass == 0) "CREATE" else "REOPEN"}") {
                    val handle = if (installConstraints) {
                        LocalLedgerDatabase.open(context, subject)
                    } else {
                        EncryptedRoomFactory(context).open(subject, LocalLedgerDatabase::class.java)
                    }
                    handle.use {
                        // The returned handle must already be open, not an apparently healthy lazy DB.
                        assertTrue(it.database.isOpen)
                        val db = it.database.openHelper.writableDatabase
                        assertEquals(3, db.version)
                        assertForeignKeys(db)
                        if (pass == 0) insertCategory(db)
                        assertCategory(db)
                        if (installConstraints) assertCategoryConstraint(db)
                    }
                }
            }
            assertFalse(file.readBytes().take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
        } finally {
            // Cleanup also runs when the first open fails; never delete the shared Keystore alias.
            deleteSyntheticFiles(file)
            listOf(".key", ".key.bak", ".key.new").forEach {
                File(file.path.removeSuffix(".db") + it).delete()
            }
        }
    }

    @Test fun productionFactoryOpensWithoutLedgerCallbacksAndReopensAfterClose() =
        productionFactory(installConstraints = false)

    @Test fun productionLedgerOpensWithConstraintsAndReopensAfterClose() =
        productionFactory(installConstraints = true)
}
