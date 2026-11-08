package dev.expensetracker.model

/** Once-only thresholds for signed NET spending; this does not post notifications. */
object BudgetThresholds {
    fun reached(limitPaise: Long, spentPaise: Long, alreadyEmitted: Set<Int> = emptySet()): Set<Int> {
        Inr.requireAmount(limitPaise)
        require(alreadyEmitted.all { it == 80 || it == 100 }) { "INVALID_BUDGET_THRESHOLD" }
        if (spentPaise < 0) return emptySet()
        return listOf(80, 100).filterTo(linkedSetOf()) { threshold ->
            // The validated budget is at most 10^11 paise, so this product and ceiling cannot overflow.
            val minimumPaise = (limitPaise * threshold + 99) / 100
            threshold !in alreadyEmitted && spentPaise >= minimumPaise
        }
    }
}
