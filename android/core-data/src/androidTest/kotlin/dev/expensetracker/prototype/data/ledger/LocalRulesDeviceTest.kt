package dev.expensetracker.prototype.data.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.expensetracker.model.TransactionKind
import dev.expensetracker.prototype.data.DatabaseKeyVault
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic encrypted Room integration checks; compile on host, device execution deferred. */
@RunWith(AndroidJUnit4::class)
class LocalRulesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private fun id() = UUID.randomUUID()
    private fun withDatabase(test: suspend (LocalLedgerDatabase) -> Unit) = runBlocking {
        val subject = "synthetic-rules-${id()}"
        val file = DatabaseKeyVault(context).databaseFile(subject)
        val handle = LocalLedgerDatabase.open(context, subject)
        try { test(handle.database) } finally {
            handle.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            listOf(".key", ".key.bak", ".key.new").forEach { File(file.path.removeSuffix(".db") + it).delete() }
        }
    }
    private suspend fun account(db: LocalLedgerDatabase): UUID = id().also {
        db.ledger().insertAccount(AccountAliasEntity(it, "SBI", "bank", "1234", "Synthetic account", "active", now))
    }
    private suspend fun category(db: LocalLedgerDatabase, name: String = "Groceries"): UUID = id().also {
        db.ledger().insertCategory(PersonalCategoryEntity(it, null, name))
    }
    private fun failed(block: suspend () -> Unit) {
        assertThrows(RuntimeException::class.java) { runBlocking { block() } }
    }

    @Test fun merchantSpecificityExactMatchingAndFutureOnlyStorage() = withDatabase { db ->
        val a = account(db)
        val b = account(db)
        val general = MerchantRuleEntity(id(), "synthetic grocer", null, category(db), now)
        val specific = general.copy(id = id(), accountId = a, categoryId = category(db, "Transport"))
        val record = LocalTransactionEntity(id = id(), accountId = a, amountPaise = 100L,
            kind = TransactionKind.EXPENSE, reportDate = LocalDate.of(2026, 11, 8), occurredAt = now,
            observedAt = now, dateSource = "event", merchantLabel = "Synthetic Grocer", categoryId = general.categoryId,
            confidence = "high", reasonCode = "SYNTHETIC", createdAt = now, updatedAt = now)
        val evidence = CaptureObservationEntity("a".repeat(64), record.id, ObservationSource.SMS,
            "synthetic.v1", 1, now, "SBI", ObservationOutcome.CAPTURED, "SYNTHETIC")
        db.ledger().recordDetected(record, evidence)
        db.rules().putMerchantRule(general)
        db.rules().putMerchantRule(specific)
        assertEquals(specific, db.rules().merchantRule(general.merchantKey, a))
        assertEquals(general, db.rules().merchantRule(general.merchantKey, b))
        assertEquals(general, db.rules().merchantRule(general.merchantKey, null))
        assertNull(db.rules().merchantRule("synthetic", a))
        assertNull(db.rules().merchantRule("other", a))
        db.rules().putMerchantRule(specific.copy(categoryId = general.categoryId, updatedAt = now.plusSeconds(1)))
        assertEquals(record, db.ledger().transaction(record.id))
        db.ledger().archiveCategory(specific.categoryId, now)
        failed { db.rules().putMerchantRule(specific) }
        failed { db.rules().putMerchantRule(general.copy(id = id(), merchantKey = "orphan", accountId = id())) }
        failed { db.rules().putMerchantRule(general.copy(id = id(), merchantKey = "orphan", categoryId = id())) }
    }

    @Test fun merchantScopeUniquenessIsEnforcedForNullAndConcreteAccountsInSQL() = withDatabase { db ->
        val account = account(db)
        val rule = MerchantRuleEntity(id(), "synthetic shop", null, category(db), now)
        db.rules().putMerchantRule(rule)
        val sql = db.openHelper.writableDatabase
        fun insert(scope: UUID?) = sql.execSQL(
            "INSERT INTO merchant_rule (id,merchantKey,accountId,categoryId,updatedAt) VALUES (?,?,?,?,?)",
            arrayOf(id().toString(), rule.merchantKey, scope?.toString(), rule.categoryId.toString(), LedgerConverters().instantToText(now)))
        assertThrows(RuntimeException::class.java) { insert(null) }
        insert(account)
        assertThrows(RuntimeException::class.java) { insert(account) }
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE merchant_rule SET accountId = NULL WHERE accountId IS NOT NULL") }
        assertEquals(rule, db.rules().merchantRule(rule.merchantKey, null))
    }

    @Test fun sharingReturnsAllConflictsOnlyForEnabledExactHouseholdMatchesWithoutChanges() = withDatabase { db ->
        val a = account(db)
        val b = account(db)
        val household = id()
        val merchant = SharingRuleEntity(id(), household, "synthetic shop", null, id(), true, now)
        val accountOnly = merchant.copy(id = id(), merchantKey = null, accountId = a, householdCategoryId = id())
        val both = merchant.copy(id = id(), accountId = a, householdCategoryId = id())
        val disabled = merchant.copy(id = id(), enabled = false)
        val otherHousehold = merchant.copy(id = id(), householdId = id())
        for (rule in listOf(merchant, accountOnly, both, disabled, otherHousehold)) db.rules().putSharingRule(rule)
        assertEquals(setOf(merchant, accountOnly, both), db.rules().matchingSharingRules(household, "synthetic shop", a).toSet())
        assertEquals(setOf(merchant), db.rules().matchingSharingRules(household, "synthetic shop", b).toSet())
        assertEquals(setOf(accountOnly), db.rules().matchingSharingRules(household, "other", a).toSet())
        assertEquals(setOf(accountOnly), db.rules().matchingSharingRules(household, null, a).toSet())
        assertEquals(setOf(merchant), db.rules().matchingSharingRules(household, "synthetic shop", null).toSet())
        assertTrue(db.rules().matchingSharingRules(household, null, null).isEmpty())
        assertTrue(db.rules().matchingSharingRules(id(), "synthetic shop", a).isEmpty())
        assertEquals(setOf(merchant, accountOnly, both), db.rules().matchingSharingRules(household, "synthetic shop", a).toSet())
        failed { db.rules().putSharingRule(both.copy(id = id(), accountId = id())) }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_transaction").use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun undoExpiryIsInclusiveAndPurgeDoesNotApplyInverseChanges() = withDatabase { db ->
        val payload = CorrectionUndoPayload(listOf(TransactionCategoryUndo(id(), null, 2)))
        val expired = CorrectionUndoEntity(id(), payload, now, now.plusSeconds(10))
        val live = expired.copy(operationId = id(), expiresAt = now.plusSeconds(20))
        db.rules().putUndo(expired)
        db.rules().putUndo(live)
        assertEquals(expired, db.rules().undo(expired.operationId, expired.expiresAt.minusNanos(1)))
        assertNull(db.rules().undo(expired.operationId, expired.expiresAt))
        assertNull(db.rules().undo(id(), now))
        assertEquals(1, db.rules().purgeExpiredUndo(expired.expiresAt))
        assertEquals(live, db.rules().undo(live.operationId, expired.expiresAt))
        assertEquals(0, db.rules().purgeExpiredUndo(expired.expiresAt))
        assertEquals(1, db.rules().purgeExpiredUndo(live.expiresAt))
    }

    @Test fun coverageRequiresExplicitDigestBoundConfirmationWithNoUnresolvedRows() = withDatabase { db ->
        val check = CoverageCheckEntity(id(), account(db), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8),
            "b".repeat(64), 3, 1, 2, 0)
        db.rules().putCoverage(check)
        assertEquals(check, db.rules().coverage(check.id))
        assertNull(db.rules().coverage(id()))
        assertEquals(0, db.rules().confirmCoverage(check.id, "c".repeat(64), now))
        assertNull(db.rules().coverage(check.id)?.confirmedAt)
        val unresolved = check.copy(id = id(), newCount = 1, unresolvedCount = 1)
        db.rules().putCoverage(unresolved)
        assertEquals(0, db.rules().confirmCoverage(unresolved.id, unresolved.statementDigest, now))
        failed { db.rules().putCoverage(check.copy(id = id(), confirmedAt = now)) }
        failed { db.rules().putCoverage(check.copy(id = id(), accountId = id())) }
        assertEquals(1, db.rules().confirmCoverage(check.id, check.statementDigest, now))
        assertEquals(now, db.rules().coverage(check.id)?.confirmedAt)
        assertEquals(0, db.rules().confirmCoverage(check.id, check.statementDigest, now.plusSeconds(1)))
        assertEquals(now, db.rules().coverage(check.id)?.confirmedAt)
        assertEquals(0, db.rules().confirmCoverage(id(), check.statementDigest, now))
    }
}
