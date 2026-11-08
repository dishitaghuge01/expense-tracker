package dev.expensetracker.model

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class BudgetThresholdsTest {
    @Test fun `exact and immediately below thresholds use integer paise`() {
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(100, 79))
        assertEquals(setOf(80), BudgetThresholds.reached(100, 80))
        assertEquals(setOf(80), BudgetThresholds.reached(100, 99))
        assertEquals(setOf(80, 100), BudgetThresholds.reached(100, 100))
        assertEquals(setOf(80, 100), BudgetThresholds.reached(100, 101))
    }

    @Test fun `fractional paise thresholds round up never down`() {
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(101, 80))
        assertEquals(setOf(80), BudgetThresholds.reached(101, 81))
        assertEquals(setOf(80), BudgetThresholds.reached(101, 100))
        assertEquals(setOf(80, 100), BudgetThresholds.reached(101, 101))
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(1, 0))
        assertEquals(setOf(80, 100), BudgetThresholds.reached(1, 1))
    }

    @Test fun `signed net spending handles refunds and Long extrema without overflow`() {
        for (limit in listOf(1L, 101L, Inr.MAX_AMOUNT_PAISE)) {
            for (spent in listOf(Long.MIN_VALUE, -1L, 0L)) assertEquals(emptySet<Int>(), BudgetThresholds.reached(limit, spent))
            assertEquals(setOf(80, 100), BudgetThresholds.reached(limit, Long.MAX_VALUE))
        }
    }

    @Test fun `past emission is suppressed even after refund and recrossing`() {
        assertEquals(setOf(100), BudgetThresholds.reached(100, 150, setOf(80)))
        assertEquals(setOf(80), BudgetThresholds.reached(100, 150, setOf(100)))
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(100, 30, setOf(80)))
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(100, 85, setOf(80)))
        assertEquals(emptySet<Int>(), BudgetThresholds.reached(100, Long.MAX_VALUE, setOf(80, 100)))
    }

    @Test fun `invalid budgets and malformed emitted threshold sets are rejected`() {
        for (limit in listOf(Long.MIN_VALUE, -1L, 0L, Inr.MAX_AMOUNT_PAISE + 1, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { BudgetThresholds.reached(limit, 0) }
        }
        for (emitted in listOf(setOf(0), setOf(79), setOf(81), setOf(101), setOf(80, -1))) {
            assertThrows(IllegalArgumentException::class.java) { BudgetThresholds.reached(100, Long.MIN_VALUE, emitted) }
        }
    }

    @Test fun `boundary oracle covers small and maximum budgets without floating arithmetic`() {
        for (limit in (1L..205L).toList() + listOf(Inr.MAX_AMOUNT_PAISE - 1, Inr.MAX_AMOUNT_PAISE)) {
            val boundaries = listOf(80, 100).map { threshold ->
                BigInteger.valueOf(limit).multiply(BigInteger.valueOf(threshold.toLong()))
                    .add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100)).longValueExact()
            }
            for (spent in boundaries.flatMap { listOf(it - 1, it, it + 1) }) {
                val expected = listOf(80, 100).zip(boundaries).filter { spent >= it.second }.map { it.first }.toSet()
                assertEquals("limit=$limit spent=$spent", expected, BudgetThresholds.reached(limit, spent))
            }
        }
    }
}
