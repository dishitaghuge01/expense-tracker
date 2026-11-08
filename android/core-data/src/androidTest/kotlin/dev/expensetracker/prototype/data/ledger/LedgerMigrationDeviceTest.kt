package dev.expensetracker.prototype.data.ledger

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.expensetracker.prototype.data.DatabaseKeyVault
import dev.expensetracker.prototype.data.PreservingCipherFactory
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opens an actual encrypted frozen v1 fixture through the production v2 opener. No phone run yet. */
@RunWith(AndroidJUnit4::class)
class LedgerMigrationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun rows(db: SupportSQLiteDatabase, query: String): List<List<String?>> = db.query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
    }

    @Test fun additiveMigrationPreservesAllOldRowsLabelsMetadataAndTriggers() = runBlocking {
        val subject = "synthetic-v1-migration-${UUID.randomUUID()}"
        val vault = DatabaseKeyVault(context)
        val file = vault.databaseFile(subject)
        val secret = vault.secret(subject)
        val account = UUID.randomUUID()
        val category = UUID.randomUUID()
        val archived = UUID.randomUUID()
        val transaction = UUID.randomUUID()
        val shared = UUID.randomUUID()
        val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
        val timestamp = LedgerConverters().instantToText(now)
        val tables = listOf("account_alias", "personal_category", "personal_budget", "local_transaction", "capture_observation")
        val before = mutableMapOf<String, List<List<String?>>>()
        var triggers: List<List<String?>> = emptyList()
        try {
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())
            val callback = object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) { LedgerV1Fixture.create(db) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { error("NOT_A_V1_FIXTURE") }
            }
            PreservingCipherFactory(secret).create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath).callback(callback).build()).use { helper ->
                val db = helper.writableDatabase
                assertEquals(1, db.version)
                db.execSQL("INSERT INTO account_alias VALUES (?,?,?,?,?,?,?)", arrayOf(account.toString(), "SBI", "bank", "1234", "Synthetic salary", "active", timestamp))
                db.execSQL("INSERT INTO personal_category VALUES (?,?,?,?)", arrayOf(category.toString(), "groceries", "Synthetic Café", null))
                db.execSQL("INSERT INTO personal_category VALUES (?,?,?,?)", arrayOf(archived.toString(), null, "Old synthetic label", timestamp))
                db.execSQL("INSERT INTO personal_budget VALUES (?,?,?,?)", arrayOf(category.toString(), "2026-11", 123456L, timestamp))
                db.execSQL("""INSERT INTO local_transaction
                    (id,accountId,amountPaise,kind,reportDate,occurredAt,observedAt,dateSource,merchantLabel,categoryId,
                    beneficiaryLabel,localReferenceDigest,confidence,reasonCode,status,refundOfId,localVersion,sharedId,
                    shareVersion,shareState,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    arrayOf(transaction.toString(), account.toString(), 85000L, "expense", "2026-11-08", timestamp,
                        timestamp, "event", "Synthetic Café", category.toString(), "Household", "b".repeat(64), "high",
                        "SYNTHETIC", "confirmed", null, 7L, shared.toString(), 3L, "shared", timestamp, timestamp))
                db.execSQL("INSERT INTO capture_observation VALUES (?,?,?,?,?,?,?,?,?)", arrayOf("a".repeat(64), transaction.toString(), "sms", "synthetic.v1", 1, timestamp, "SBI", "captured", "SYNTHETIC"))
                // An independently named old trigger detects destructive reconstruction even if rows are copied.
                db.execSQL("""CREATE TRIGGER synthetic_v1_sentinel BEFORE DELETE ON account_alias
                    BEGIN SELECT RAISE(ABORT, 'SYNTHETIC_V1_SENTINEL'); END""")
                for (table in tables) before[table] = rows(db, "SELECT * FROM $table ORDER BY 1")
                triggers = rows(db, "SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name")
                assertEquals(listOf(listOf("381874798e240c3088f5f6a019e16a4a")), rows(db, "SELECT identity_hash FROM room_master_table WHERE id=42"))
            }
            assertFalse(file.readBytes().take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
            LocalLedgerDatabase.open(context, subject).use { handle ->
                val db = handle.database
                val sql = db.openHelper.writableDatabase
                assertEquals(2, sql.version)
                for (table in tables) assertEquals("Preserve $table", before[table], rows(sql, "SELECT * FROM $table ORDER BY 1"))
                val afterTriggers = rows(sql, "SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name")
                assertTrue(afterTriggers.containsAll(triggers))
                assertTrue(rows(sql, "PRAGMA foreign_key_check").isEmpty())
                assertEquals(7L, db.ledger().transaction(transaction)?.localVersion)
                assertEquals(shared, db.ledger().transaction(transaction)?.sharedId)
                assertEquals(3L, db.ledger().transaction(transaction)?.shareVersion)
                assertEquals(now, db.ledger().category(archived)?.archivedAt)
                assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE local_transaction SET amountPaise=1") }
                assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE local_transaction SET reportDate='2026-11-09'") }
                assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE personal_budget SET limitPaise=0") }
                assertThrows(RuntimeException::class.java) { sql.execSQL("INSERT INTO personal_category VALUES (?,?,?,NULL)", arrayOf(UUID.randomUUID().toString(), null, "synthetic café")) }
                val rule = MerchantRuleEntity(UUID.randomUUID(), "synthetic café", account, category, now)
                db.rules().putMerchantRule(rule)
                assertEquals(rule, db.rules().merchantRule(rule.merchantKey, account))
                for (table in listOf("sharing_rule", "correction_undo", "coverage_check")) {
                    assertEquals(listOf(listOf("0")), rows(sql, "SELECT COUNT(*) FROM $table"))
                }
            }
            LocalLedgerDatabase.open(context, subject).use {
                assertEquals(85000L, it.database.ledger().transaction(transaction)?.amountPaise)
                assertEquals("Synthetic Café", it.database.ledger().transaction(transaction)?.merchantLabel)
            }
        } finally {
            secret.fill(0)
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            listOf(".key", ".key.bak", ".key.new").forEach { File(file.path.removeSuffix(".db") + it).delete() }
        }
    }
}
