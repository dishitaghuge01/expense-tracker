package dev.expensetracker.prototype.data.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.expensetracker.model.TransactionKind
import dev.expensetracker.model.TransactionStatus
import dev.expensetracker.prototype.data.DatabaseKeyVault
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room+SQLCipher tests using synthetic records only. Device execution is deferred. */
@RunWith(AndroidJUnit4::class)
class LocalLedgerDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private fun id() = UUID.randomUUID()
    private fun record() = LocalTransactionEntity(
        id = id(), accountId = null, amountPaise = 85_000L, kind = TransactionKind.EXPENSE,
        reportDate = LocalDate.of(2026, 11, 8), occurredAt = now, observedAt = now,
        dateSource = "event", merchantLabel = "Synthetic Grocer", categoryId = null,
        confidence = "high", reasonCode = "SYNTHETIC_TEMPLATE", createdAt = now, updatedAt = now,
    )
    private fun observation(record: LocalTransactionEntity) = CaptureObservationEntity(
        fingerprint = record.id.toString().replace("-", "").repeat(2), transactionId = record.id,
        source = ObservationSource.SMS, templateId = "synthetic.v1", parserVersion = 1, observedAt = now,
        institutionCode = "SBI", outcome = ObservationOutcome.CAPTURED, reasonCode = "SYNTHETIC_TEMPLATE",
    )

    private fun withDatabase(test: suspend (String, LocalLedgerDatabase) -> Unit) = runBlocking {
        val subject = "synthetic-room-test-${id()}"
        val file = DatabaseKeyVault(context).databaseFile(subject)
        val handle = LocalLedgerDatabase.open(context, subject)
        try {
            test(subject, handle.database)
        } finally {
            handle.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            listOf(".key", ".key.bak", ".key.new").forEach {
                File(file.path.removeSuffix(".db") + it).delete()
            }
        }
    }

    @Test fun duplicateReplayPreservesManualCorrectionAndCountsOnlyOneRecord() = withDatabase { _, db ->
        val dao = db.ledger()
        val record = record()
        val evidence = observation(record)
        assertTrue(dao.recordDetected(record, evidence).inserted)
        val category = PersonalCategoryEntity(id(), null, "Groceries")
        dao.insertCategory(category)
        assertEquals(1, dao.changeCategory(record.id, category.id, 1, now))
        val replay = record.copy(id = id())
        assertFalse(dao.recordDetected(replay, evidence.copy(transactionId = replay.id)).inserted)
        assertNull(dao.transaction(replay.id))
        assertEquals(category.id, dao.transaction(record.id)?.categoryId)
        assertEquals(1, dao.observeTransactions(record.reportDate, record.reportDate).first().size)
    }

    @Test fun distinctRepeatedPaymentsStaySeparateAndForeignKeysAreEnforced() = withDatabase { _, db ->
        val dao = db.ledger()
        val first = record()
        val second = first.copy(id = id())
        dao.recordDetected(first, observation(first))
        dao.recordDetected(second, observation(second))
        assertEquals(2, dao.observeTransactions(first.reportDate, first.reportDate).first().size)
        db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        val invalid = record().copy(accountId = id())
        assertThrows(RuntimeException::class.java) { runBlocking { dao.recordDetected(invalid, observation(invalid)) } }
        assertNull(dao.transaction(invalid.id))
    }

    @Test fun newRecordMustBePrivateAndObservationFailureRollsBackBothWrites() = withDatabase { _, db ->
        val dao = db.ledger()
        val notPrivate = record().copy(sharedId = id(), shareState = "shared", shareVersion = 1)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { dao.recordDetected(notPrivate, observation(notPrivate)) } }
        assertNull(dao.transaction(notPrivate.id))
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER synthetic_fail_observation BEFORE INSERT ON capture_observation
            BEGIN SELECT RAISE(ABORT, 'SYNTHETIC_FAILURE'); END""")
        val record = record()
        assertThrows(RuntimeException::class.java) { runBlocking { dao.recordDetected(record, observation(record)) } }
        assertNull(dao.transaction(record.id))
        assertNull(dao.observation(observation(record).fingerprint))
    }

    @Test fun activeNamesAreUniqueAndArchivingPreservesHistory() = withDatabase { _, db ->
        val dao = db.ledger()
        val first = PersonalCategoryEntity(id(), null, "Groceries")
        dao.insertCategory(first)
        val duplicate = first.copy(id = id(), name = "groceries")
        assertThrows(RuntimeException::class.java) { runBlocking { dao.insertCategory(duplicate) } }
        dao.archiveCategory(first.id, now)
        dao.insertCategory(duplicate)
        assertEquals(now, dao.category(first.id)?.archivedAt)
        assertEquals(listOf(duplicate), dao.observeActiveCategories().first())
        val other = PersonalCategoryEntity(id(), null, "Transport")
        dao.insertCategory(other)
        assertThrows(RuntimeException::class.java) { runBlocking { dao.renameCategory(other.id, "GROCERIES") } }
    }

    @Test fun SQLCannotChangeCapturedAmountDateOrSetAnInvalidBudget() = withDatabase { _, db ->
        val dao = db.ledger()
        val record = record()
        dao.recordDetected(record, observation(record))
        val sql = db.openHelper.writableDatabase
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE local_transaction SET amountPaise = 1") }
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE local_transaction SET reportDate = '2026-11-09'") }
        assertEquals(record, dao.transaction(record.id))
        val category = PersonalCategoryEntity(id(), null, "Groceries")
        dao.insertCategory(category)
        dao.setBudget(PersonalBudgetEntity(category.id, YearMonth.of(2026, 11), 100_000L, now))
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE personal_budget SET limitPaise = 0") }
        assertEquals(100_000L, dao.observeBudgets(YearMonth.of(2026, 11)).first().single().limitPaise)
    }

    @Test fun encryptedRoomReopensWithNanosAndDateOnlyValuesIntact() = withDatabase { subject, db ->
        val record = record().copy(occurredAt = null, dateSource = "statement")
        db.ledger().recordDetected(record, observation(record).copy(source = ObservationSource.CSV))
        LocalLedgerDatabase.open(context, subject).use { reopened ->
            assertEquals(record, reopened.database.ledger().transaction(record.id))
        }
    }

    @Test fun budgetsAreMonthlyAndCannotBeAssignedToArchivedCategories() = withDatabase { _, db ->
        val dao = db.ledger()
        val category = PersonalCategoryEntity(id(), null, "Groceries")
        dao.insertCategory(category)
        val budget = PersonalBudgetEntity(category.id, YearMonth.of(2026, 11), 100_000L, now)
        dao.setBudget(budget)
        assertEquals(listOf(budget), dao.observeBudgets(budget.month).first())
        assertTrue(dao.observeBudgets(YearMonth.of(2026, 12)).first().isEmpty())
        dao.archiveCategory(category.id, now)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { dao.setBudget(budget) } }
    }

    @Test fun correctionsAreVersionCheckedAndDoNotConfirmOrAlterExistingShareMetadata() = withDatabase { _, db ->
        val dao = db.ledger()
        val record = record()
        dao.recordDetected(record, observation(record))
        val category = PersonalCategoryEntity(id(), null, "Groceries")
        dao.insertCategory(category)
        assertEquals(1, dao.changeCategory(record.id, category.id, 1, now))
        assertEquals(0, dao.changeCategory(record.id, null, 1, now))
        val corrected = dao.transaction(record.id)!!
        assertEquals(2L, corrected.localVersion)
        assertEquals(TransactionStatus.REVIEW, corrected.status)
        assertNull(corrected.sharedId)
        assertEquals(record.amountPaise, corrected.amountPaise)
        assertEquals(record.reportDate, corrected.reportDate)
    }
}
