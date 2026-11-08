package dev.expensetracker.prototype.data.ledger

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LocalRulesValuesTest {
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private val id = UUID(0, 1)
    private val account = UUID(0, 2)
    private val category = UUID(0, 3)
    private fun rule() = MerchantRuleEntity(id, "synthetic café", null, category, now)
    private fun snapshot() = MerchantRuleSnapshot(id, "synthetic café", null, category, now)
    private fun payload() = CorrectionUndoPayload(listOf(TransactionCategoryUndo(id, null, 2L)))
    private fun coverage() = CoverageCheckEntity(id, account, LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 8), "a".repeat(64), 3, 1, 1, 1, null)
    private fun invalid(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java) { block() } }

    @Test fun `merchant keys are already normalized exact lowercase ROOT values`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("indigo", rule().copy(merchantKey = "indigo").merchantKey)
            for (key in listOf("", " ", " shop", "shop ", "SHOP", "cafe\u0301", "shop\nname", "x".repeat(81))) {
                invalid { rule().copy(merchantKey = key) }
                invalid { snapshot().copy(merchantKey = key) }
            }
            assertEquals(80, rule().copy(merchantKey = "x".repeat(80)).merchantKey.length)
        } finally { Locale.setDefault(original) }
    }

    @Test fun `account scopes remain distinct and sharing requires at least one exact key`() {
        assertNotEquals(rule(), rule().copy(accountId = account))
        val sharing = SharingRuleEntity(id = id, householdId = UUID(0, 4), merchantKey = "synthetic café",
            accountId = null, householdCategoryId = category, confirmedAt = now)
        assertFalse(sharing.enabled)
        invalid { sharing.copy(merchantKey = null) }
        assertEquals(account, sharing.copy(merchantKey = null, accountId = account).accountId)
        invalid { sharing.copy(merchantKey = "SHOP") }
    }

    @Test fun `undo DTO requires positive versions unique transactions and a change`() {
        for (version in listOf(0L, -1L)) invalid { TransactionCategoryUndo(id, null, version) }
        invalid { CorrectionUndoPayload(emptyList()) }
        invalid { CorrectionUndoPayload(listOf(TransactionCategoryUndo(id, null, 1), TransactionCategoryUndo(id, category, 2))) }
        assertEquals(snapshot(), CorrectionUndoPayload(emptyList(), priorRule = snapshot()).priorRule)
        val before = snapshot()
        val after = before.copy(categoryId = UUID(0, 9), updatedAt = now.plusSeconds(1))
        assertEquals(after, CorrectionUndoPayload(emptyList(), before, after).postRule)
        for (bad in listOf(after.copy(id = UUID(0, 8)), after.copy(accountId = account), after.copy(merchantKey = "other"))) {
            invalid { CorrectionUndoPayload(emptyList(), before, bad) }
        }
    }

    @Test fun `strict structured JSON round trips null categories nanos and rule snapshots`() {
        val values = listOf(payload(), CorrectionUndoPayload(emptyList(), priorRule = snapshot()),
            CorrectionUndoPayload(emptyList(), postRule = snapshot().copy(accountId = account)),
            CorrectionUndoPayload(payload().transactions, snapshot(), snapshot().copy(categoryId = UUID(0, 9))))
        values.forEach { assertEquals(it, CorrectionUndoCodec.decode(CorrectionUndoCodec.encode(it))) }
    }

    @Test fun `JSON rejects unknown raw fields wrong types malformed values and invalid state without echo`() {
        val encoded = CorrectionUndoCodec.encode(payload())
        val snapshotJson = CorrectionUndoCodec.encode(CorrectionUndoPayload(emptyList(), priorRule = snapshot()))
        val marker = "SYNTHETIC_PRIVATE_MARKER"
        val invalidJson = listOf(
            encoded.replaceFirst("{", "{\"rawMessage\":\"$marker\","),
            encoded.replace("\"postEditVersion\":2", "\"postEditVersion\":2.5"),
            encoded.replace("\"postEditVersion\":2", "\"postEditVersion\":\"2\""),
            encoded.replace("\"postEditVersion\":2", "\"postEditVersion\":0"),
            encoded.replace(id.toString(), marker),
            "{\"transactions\":[{\"transactionId\":\"$id\",\"previousCategoryId\":false,\"postEditVersion\":2}]}",
            snapshotJson.replace(now.toString(), marker),
            "{\"transactions\":[]}", "{\"transactions\":false}", "[]", "null", "{", marker.repeat(1000),
            "{\"transactions\":[{\"transactionId\":\"$id\",\"previousCategoryId\":null,\"postEditVersion\":1},{\"transactionId\":\"$id\",\"previousCategoryId\":null,\"postEditVersion\":2}]}",
            encoded.replaceFirst("{", "{\"formatVersion\":999,"),
            snapshotJson.replace("\"merchantKey\":\"synthetic café\"", "\"merchantKey\":false"),
            "{\"transactions\":[{\"transactionId\":\"$id\",\"previousCategoryId\":null,\"postEditVersion\":1,\"amount\":123}]}"
        )
        invalidJson.forEach { text ->
            val error = assertThrows(IllegalArgumentException::class.java) { CorrectionUndoCodec.decode(text) }
            assertTrue((error.message ?: "").length <= 200)
            assertFalse((error.message ?: "").contains(marker))
        }
    }

    @Test fun `undo lifetime is positive and never exceeds 24 hours`() {
        val undo = CorrectionUndoEntity(id, payload(), now, now.plusSeconds(86400))
        assertEquals(now.plusSeconds(86400), undo.expiresAt)
        for (expiry in listOf(now.minusNanos(1), now, now.plusSeconds(86400).plusNanos(1))) {
            invalid { undo.copy(expiresAt = expiry) }
        }
        assertEquals(now.plusNanos(1), undo.copy(expiresAt = now.plusNanos(1)).expiresAt)
    }

    @Test fun `coverage counts partition bounded rows and dates and digest are strict`() {
        val check = coverage()
        assertNull(check.confirmedAt)
        assertEquals(0, check.copy(rowCount = 0, matchedCount = 0, newCount = 0, unresolvedCount = 0).rowCount)
        assertEquals(10000, check.copy(rowCount = 10000, matchedCount = 9998).rowCount)
        invalid { check.copy(rowCount = 10001, matchedCount = 9999) }
        invalid { check.copy(rowCount = -1) }
        invalid { check.copy(matchedCount = -1) }
        invalid { check.copy(newCount = -1) }
        invalid { check.copy(unresolvedCount = -1) }
        invalid { check.copy(matchedCount = 2) }
        invalid { check.copy(rowCount = 0, matchedCount = Int.MAX_VALUE, newCount = Int.MAX_VALUE, unresolvedCount = 2) }
        invalid { check.copy(fromDate = check.toDate.plusDays(1)) }
        assertEquals(check.toDate, check.copy(fromDate = check.toDate).fromDate)
        for (digest in listOf("A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65), "raw statement")) {
            invalid { check.copy(statementDigest = digest) }
        }
    }
}
