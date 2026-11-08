package dev.expensetracker.model

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingTest {
    private fun id(value: Int) = UUID(0, value.toLong())
    private val personal = LedgerKey(LedgerKind.PERSONAL, id(1))
    private val household = LedgerKey(LedgerKind.HOUSEHOLD, id(2))
    private val category = id(3)
    private val month = YearMonth.of(2026, 11)

    private fun expense(number: Int = 10, amount: Long = 10_000L) = AccountingRecord(
        id(number), personal, id(1), TransactionKind.EXPENSE, amount,
        LocalDate.of(2026, 11, 8), category,
    )

    private fun refund(number: Int, original: AccountingRecord, amount: Long = 2_000L) = original.copy(
        id = id(number), kind = TransactionKind.REFUND, amountPaise = amount, refundOfId = original.id,
        status = TransactionStatus.CONFIRMED, state = SpendingState.INCLUDED,
    )

    private fun summarize(vararg records: AccountingRecord) = Accounting.summarize(personal, month, records.toList())

    @Test fun `empty ledger has zero totals but does not assert verified coverage`() {
        val result = summarize()
        assertEquals(SpendingTotals(0, 0), result.totals)
        assertEquals(0L, result.totals.netPaise)
        assertEquals(emptyMap<UUID?, SpendingTotals>(), result.categories)
        assertEquals(emptySet<UUID>(), result.reviewIds)
    }

    @Test fun `golden household accounting excludes non-spending and shows withdrawals separately`() {
        val purchase = expense(amount = 85_000L)
        val second = expense(11, 24_000L)
        val partialRefund = refund(12, purchase, 5_000L)
        val otherKinds = listOf(
            TransactionKind.INCOMING, TransactionKind.TRANSFER, TransactionKind.REPAYMENT,
            TransactionKind.WALLET_TOPUP, TransactionKind.WITHDRAWAL, TransactionKind.FAILED,
            TransactionKind.PENDING, TransactionKind.UNKNOWN, TransactionKind.REVERSAL,
        ).mapIndexed { index, kind -> expense(100 + index, 99_000L).copy(kind = kind) }
        val records = (listOf(purchase, second, partialRefund) + otherKinds).map { it.copy(ledger = household) }
        val result = Accounting.summarize(household, month, records)
        assertEquals(SpendingTotals(109_000, 5_000), result.totals)
        assertEquals(104_000L, result.totals.netPaise)
        assertEquals(99_000L, result.withdrawalsPaise)
        assertEquals(mapOf(category to result.totals), result.categories)
    }

    @Test fun `review excluded merged and reversed purchases never increase spending`() {
        val records = listOf(
            expense(10).copy(status = TransactionStatus.REVIEW),
            expense(11).copy(status = TransactionStatus.EXCLUDED),
            expense(12).copy(status = TransactionStatus.MERGED),
            expense(13).copy(state = SpendingState.REVERSED),
        )
        val result = summarize(*records.toTypedArray())
        assertEquals(SpendingTotals(0, 0), result.totals)
        assertEquals(setOf(id(10)), result.reviewIds)
    }

    @Test fun `refund posts in its own month even when original is from an earlier month`() {
        val purchase = expense().copy(reportDate = LocalDate.of(2026, 10, 31))
        val adjustment = refund(11, purchase).copy(reportDate = LocalDate.of(2026, 11, 1))
        val records = listOf(purchase, adjustment)
        val october = Accounting.summarize(personal, YearMonth.of(2026, 10), records)
        val november = Accounting.summarize(personal, month, records)
        assertEquals(SpendingTotals(10_000, 0), october.totals)
        assertEquals(SpendingTotals(0, 2_000), november.totals)
        assertEquals(-2_000L, november.totals.netPaise)
    }

    @Test fun `reversal cancels the original period without counting a new expense`() {
        val purchase = expense().copy(reportDate = LocalDate.of(2026, 10, 31), state = SpendingState.REVERSED)
        val event = expense(11).copy(kind = TransactionKind.REVERSAL)
        val records = listOf(purchase, event)
        assertEquals(SpendingTotals(0, 0), Accounting.summarize(personal, YearMonth.of(2026, 10), records).totals)
        assertEquals(SpendingTotals(0, 0), Accounting.summarize(personal, month, records).totals)
    }

    @Test fun `identical record replay counts once but repeat purchases stay separate`() {
        val first = expense()
        assertEquals(10_000L, summarize(first, first).totals.grossPaise)
        assertEquals(20_000L, summarize(first, first.copy(id = id(11))).totals.grossPaise)
        val error = assertThrows(IllegalArgumentException::class.java) {
            summarize(first, first.copy(amountPaise = 20_000L))
        }
        assertEquals("CONFLICTING_RECORD_ID", error.message)
    }

    @Test fun `personal and shared views are independent and mixed ledgers are rejected`() {
        val privateRecord = expense()
        val sharedRecord = privateRecord.copy(ledger = household)
        assertEquals(10_000L, summarize(privateRecord).totals.grossPaise)
        assertEquals(10_000L, Accounting.summarize(household, month, listOf(sharedRecord)).totals.grossPaise)
        assertEquals("MIXED_LEDGERS", assertThrows(IllegalArgumentException::class.java) {
            summarize(privateRecord, sharedRecord)
        }.message)
        assertThrows(IllegalArgumentException::class.java) {
            summarize(privateRecord.copy(ledger = LedgerKey(LedgerKind.PERSONAL, id(999))))
        }
    }

    @Test fun `unmatched or invalid refund links require review and never reduce spending`() {
        val purchase = expense()
        val invalid = listOf(
            refund(11, purchase).copy(refundOfId = null),
            refund(12, purchase).copy(refundOfId = id(999)),
            refund(13, purchase).copy(payerId = id(999)),
            refund(14, purchase).copy(categoryId = id(999)),
            refund(15, purchase).copy(refundOfId = id(15)),
        )
        val result = summarize(purchase, *invalid.toTypedArray())
        assertEquals(SpendingTotals(10_000, 0), result.totals)
        assertEquals(invalid.map { it.id }.toSet(), result.reviewIds)
        assertEquals(setOf(AccountingIssue.INVALID_REFUND_LINK), result.issues.values.toSet())
    }

    @Test fun `refund cannot target an excluded or reversed purchase or another refund`() {
        for (original in listOf(
            expense().copy(status = TransactionStatus.EXCLUDED),
            expense().copy(state = SpendingState.REVERSED),
            expense().copy(kind = TransactionKind.INCOMING),
            expense().copy(kind = TransactionKind.REFUND),
        )) {
            val adjustment = refund(11, original)
            val result = summarize(original, adjustment)
            assertEquals(0L, result.totals.refundsPaise)
            assertEquals(AccountingIssue.INVALID_REFUND_LINK, result.issues[adjustment.id])
        }
    }

    @Test fun `partial refunds may reach but not exceed original across months`() {
        val purchase = expense().copy(reportDate = LocalDate.of(2026, 10, 1))
        val earlier = refund(11, purchase, 6_000L)
        val later = refund(12, purchase, 4_000L).copy(reportDate = LocalDate.of(2026, 11, 8))
        assertEquals(4_000L, summarize(purchase, earlier, later).totals.refundsPaise)
        val excessive = later.copy(amountPaise = 4_001L)
        val result = summarize(purchase, earlier, excessive)
        assertEquals(0L, result.totals.refundsPaise)
        assertEquals(mapOf(excessive.id to AccountingIssue.REFUND_EXCEEDS_PURCHASE), result.issues)
    }

    @Test fun `excluded refunds do not consume outstanding amount`() {
        val purchase = expense()
        val excluded = refund(11, purchase, 10_000L).copy(status = TransactionStatus.EXCLUDED)
        val active = refund(12, purchase, 10_000L)
        assertEquals(SpendingTotals(10_000, 10_000), summarize(purchase, excluded, active).totals)
    }

    @Test fun `server-marked retained shared refund survives expired original`() {
        val retained = expense().copy(
            ledger = household, kind = TransactionKind.REFUND, refundOriginalExpired = true,
        )
        val result = Accounting.summarize(household, month, listOf(retained))
        assertEquals(SpendingTotals(0, 10_000), result.totals)
        assertEquals(emptySet<UUID>(), result.reviewIds)
        assertThrows(IllegalArgumentException::class.java) { summarize(retained.copy(ledger = personal)) }
        assertThrows(IllegalArgumentException::class.java) {
            Accounting.summarize(household, month, listOf(retained.copy(refundOfId = id(999))))
        }
    }

    @Test fun `category totals preserve uncategorized state without inference`() {
        val first = expense().copy(categoryId = null)
        val second = expense(11)
        val result = summarize(first, second, refund(12, second))
        assertEquals(mapOf(null to SpendingTotals(10_000, 0), category to SpendingTotals(10_000, 2_000)), result.categories)
    }

    @Test fun `input order does not change totals or refund issue decisions`() {
        val purchase = expense()
        val records = listOf(purchase, refund(11, purchase, 8_000L), refund(12, purchase, 8_000L))
        val expected = Accounting.summarize(personal, month, records)
        for (seed in 0..20) {
            assertEquals(expected, Accounting.summarize(personal, month, records.shuffled(kotlin.random.Random(seed))))
        }
    }

    @Test fun `record amounts stay within transaction bounds`() {
        for (amount in listOf(0L, -1L, Inr.MAX_AMOUNT_PAISE + 1)) {
            assertThrows(IllegalArgumentException::class.java) { summarize(expense(amount = amount)) }
        }
    }
}
