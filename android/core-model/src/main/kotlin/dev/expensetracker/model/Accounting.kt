package dev.expensetracker.model

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class LedgerKind { PERSONAL, HOUSEHOLD }

/** Owner subject for a personal view, or household ID for a shared view. */
data class LedgerKey(val kind: LedgerKind, val id: UUID)

enum class TransactionKind {
    EXPENSE, REFUND, REVERSAL, INCOMING, TRANSFER, REPAYMENT, WALLET_TOPUP,
    WITHDRAWAL, FAILED, PENDING, UNKNOWN,
}

enum class TransactionStatus { CONFIRMED, REVIEW, EXCLUDED, MERGED }
enum class SpendingState { INCLUDED, REVERSED }

/**
 * Accounting projection, not a persistence entity or sharing payload.
 * Callers supply authorized records from exactly one ledger, including refund originals
 * outside the requested month. A missing original is not assumed to have expired.
 */
data class AccountingRecord(
    val id: UUID,
    val ledger: LedgerKey,
    val payerId: UUID,
    val kind: TransactionKind,
    val amountPaise: Long,
    val reportDate: LocalDate,
    val categoryId: UUID?,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val state: SpendingState = SpendingState.INCLUDED,
    val refundOfId: UUID? = null,
    // Project only the server-set retention marker; never infer it from missing records.
    val refundOriginalExpired: Boolean = false,
)

data class SpendingTotals(val grossPaise: Long, val refundsPaise: Long) {
    val netPaise: Long get() = Math.subtractExact(grossPaise, refundsPaise)
}

enum class AccountingIssue { INVALID_REFUND_LINK, REFUND_EXCEEDS_PURCHASE }

data class MonthlySpending(
    val ledger: LedgerKey,
    val month: YearMonth,
    val totals: SpendingTotals,
    val categories: Map<UUID?, SpendingTotals>,
    val withdrawalsPaise: Long,
    val reviewIds: Set<UUID>,
    val issues: Map<UUID, AccountingIssue>,
)

object Accounting {
    fun summarize(ledger: LedgerKey, month: YearMonth, records: List<AccountingRecord>): MonthlySpending {
        val byId = linkedMapOf<UUID, AccountingRecord>()
        for (record in records) {
            require(record.ledger == ledger) { "MIXED_LEDGERS" }
            Inr.requireAmount(record.amountPaise)
            require(!record.refundOriginalExpired || (
                ledger.kind == LedgerKind.HOUSEHOLD && record.kind == TransactionKind.REFUND && record.refundOfId == null
            )) { "INVALID_RETENTION_MARKER" }
            require(record.kind == TransactionKind.REFUND || record.refundOfId == null) { "INVALID_REFUND_LINK" }
            val previous = byId.put(record.id, record)
            require(previous == null || previous == record) { "CONFLICTING_RECORD_ID" }
        }

        fun AccountingRecord.isIncluded() = status == TransactionStatus.CONFIRMED && state == SpendingState.INCLUDED
        fun AccountingRecord.isExpense() = kind == TransactionKind.EXPENSE && isIncluded()

        val issues = mutableMapOf<UUID, AccountingIssue>()
        val eligibleRefunds = mutableSetOf<UUID>()
        val linkedRefunds = mutableMapOf<UUID, MutableList<AccountingRecord>>()
        for (record in byId.values) {
            if (record.kind != TransactionKind.REFUND || !record.isIncluded()) continue
            if (record.refundOriginalExpired) {
                eligibleRefunds.add(record.id)
                continue
            }
            val original = byId[record.refundOfId]
            if (original == null || !original.isExpense() || original.payerId != record.payerId ||
                original.categoryId != record.categoryId
            ) {
                issues[record.id] = AccountingIssue.INVALID_REFUND_LINK
            } else {
                linkedRefunds.getOrPut(original.id) { mutableListOf() }.add(record)
            }
        }
        for ((originalId, refunds) in linkedRefunds) {
            // Validate all supplied periods together, not just this month's partial refunds.
            var outstanding = byId.getValue(originalId).amountPaise
            var exceedsPurchase = false
            for (refund in refunds) {
                if (refund.amountPaise > outstanding) {
                    exceedsPurchase = true
                    break
                }
                outstanding -= refund.amountPaise
            }
            if (exceedsPurchase) {
                // An inconsistent group needs review; do not pick a winner based on input order.
                refunds.forEach { issues[it.id] = AccountingIssue.REFUND_EXCEEDS_PURCHASE }
            } else {
                eligibleRefunds.addAll(refunds.map { it.id })
            }
        }

        var gross = 0L
        var refunds = 0L
        var withdrawals = 0L
        val categories = mutableMapOf<UUID?, SpendingTotals>()
        val reviewIds = mutableSetOf<UUID>()
        val monthIssues = mutableMapOf<UUID, AccountingIssue>()
        for (record in byId.values) {
            if (ReportingDates.reportMonth(record.reportDate) != month) continue
            if (record.status == TransactionStatus.REVIEW) reviewIds.add(record.id)
            issues[record.id]?.let {
                reviewIds.add(record.id)
                monthIssues[record.id] = it
            }
            val expense = if (record.isExpense()) record.amountPaise else 0L
            val refund = if (record.id in eligibleRefunds) record.amountPaise else 0L
            gross = Math.addExact(gross, expense)
            refunds = Math.addExact(refunds, refund)
            if (expense != 0L || refund != 0L) {
                val previous = categories[record.categoryId] ?: SpendingTotals(0L, 0L)
                categories[record.categoryId] = SpendingTotals(
                    Math.addExact(previous.grossPaise, expense), Math.addExact(previous.refundsPaise, refund),
                )
            }
            if (record.kind == TransactionKind.WITHDRAWAL && record.isIncluded()) {
                withdrawals = Math.addExact(withdrawals, record.amountPaise)
            }
        }
        return MonthlySpending(
            ledger, month, SpendingTotals(gross, refunds), categories.toMap(), withdrawals,
            reviewIds.toSet(), monthIssues.toMap(),
        )
    }
}
