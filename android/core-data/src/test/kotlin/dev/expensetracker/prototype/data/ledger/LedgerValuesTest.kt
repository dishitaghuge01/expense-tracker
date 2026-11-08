package dev.expensetracker.prototype.data.ledger

import dev.expensetracker.model.Inr
import dev.expensetracker.model.TransactionKind
import dev.expensetracker.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerValuesTest {
    private val converters = LedgerConverters()
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private val id = UUID(0, 1)

    private fun record() = LocalTransactionEntity(
        id = id, accountId = null, amountPaise = 85_000L, kind = TransactionKind.EXPENSE,
        reportDate = LocalDate.of(2026, 11, 8), occurredAt = now, observedAt = now,
        dateSource = "event", merchantLabel = "Synthetic Grocer", categoryId = null,
        confidence = "high", reasonCode = "KNOWN_SYNTHETIC_TEMPLATE", createdAt = now, updatedAt = now,
    )

    @Test fun `new records default to private review without share metadata`() {
        val record = record()
        assertEquals("private", record.shareState)
        assertEquals(TransactionStatus.REVIEW, record.status)
        assertNull(record.sharedId)
        assertNull(record.shareVersion)
    }

    @Test fun `record and budget amounts use integer-paise bounds`() {
        for (invalid in listOf(0L, -1L, Inr.MAX_AMOUNT_PAISE + 1)) {
            assertThrows(IllegalArgumentException::class.java) { record().copy(amountPaise = invalid) }
            assertThrows(IllegalArgumentException::class.java) { PersonalBudgetEntity(id, YearMonth.of(2026, 11), invalid, now) }
        }
        assertEquals(Inr.MAX_AMOUNT_PAISE, record().copy(amountPaise = Inr.MAX_AMOUNT_PAISE).amountPaise)
    }

    @Test fun `labels must be normalized bounded and free of recognized account identifiers`() {
        for (invalid in listOf("", " Groceries", "Groceries ", "Shop\nName", "e\u0301", "x".repeat(81))) {
            assertThrows(IllegalArgumentException::class.java) { PersonalCategoryEntity(id, null, invalid) }
        }
        assertEquals("Café", PersonalCategoryEntity(id, null, "Café").name)
        for (invalid in listOf("Account 123456789012", "someone@bank")) {
            assertEquals("SENSITIVE_LABEL", assertThrows(IllegalArgumentException::class.java) {
                record().copy(merchantLabel = invalid)
            }.message)
        }
    }

    @Test fun `reference and observation fields contain digests and bounded codes only`() {
        assertThrows(IllegalArgumentException::class.java) { record().copy(localReferenceDigest = "raw reference") }
        assertThrows(IllegalArgumentException::class.java) { record().copy(reasonCode = "raw message text") }
        assertEquals("a".repeat(64), record().copy(localReferenceDigest = "a".repeat(64)).localReferenceDigest)
        assertThrows(IllegalArgumentException::class.java) {
            CaptureObservationEntity("raw SMS", null, ObservationSource.SMS, null, 1, now, "SBI", ObservationOutcome.UNSUPPORTED, "UNSUPPORTED")
        }
    }

    @Test fun `event date and versions cannot contradict normalized fields`() {
        assertThrows(IllegalArgumentException::class.java) { record().copy(reportDate = LocalDate.of(2026, 11, 7)) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(localVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(shareVersion = 1) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(sharedId = UUID(0, 2), shareVersion = 0) }
    }

    @Test fun `UTC timestamps preserve nanos and sort chronologically at whole-second boundaries`() {
        val whole = Instant.parse("2026-11-08T12:34:56Z")
        assertEquals("2026-11-08T12:34:56.000000000Z", converters.instantToText(whole))
        assertEquals(now, converters.textToInstant(converters.instantToText(now)))
        assertTrue(converters.instantToText(whole) < converters.instantToText(now))
    }

    @Test fun `date month and canonical UUID values round trip`() {
        assertEquals(id, converters.textToUuid(converters.uuidToText(id)))
        assertEquals(LocalDate.of(2024, 2, 29), converters.textToDate("2024-02-29"))
        assertEquals(YearMonth.of(2026, 11), converters.textToMonth("2026-11"))
        for (invalid in listOf("1-1-1-1-1", "not-a-uuid", id.toString().uppercase(Locale.ROOT).replace("0001", "ABCD"))) {
            // Canonical lowercase is required; the final case includes actual uppercase hex digits.
            assertThrows(IllegalArgumentException::class.java) { converters.textToUuid(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { converters.dateToText(LocalDate.of(0, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { converters.monthToText(YearMonth.of(10000, 1)) }
    }

    @Test fun `stored enum codes remain lowercase and locale independent`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            TransactionKind.entries.forEach { assertEquals(it, converters.textToKind(converters.kindToText(it))) }
            TransactionStatus.entries.forEach { assertEquals(it, converters.textToStatus(converters.statusToText(it))) }
            ObservationSource.entries.forEach { assertEquals(it, converters.textToSource(converters.sourceToText(it))) }
            ObservationOutcome.entries.forEach { assertEquals(it, converters.textToOutcome(converters.outcomeToText(it))) }
            assertEquals("incoming", converters.kindToText(TransactionKind.INCOMING))
            assertEquals("INVALID_ENUM_CODE", assertThrows(IllegalArgumentException::class.java) { converters.textToKind("SECRET INPUT") }.message)
        } finally {
            Locale.setDefault(original)
        }
    }
}
