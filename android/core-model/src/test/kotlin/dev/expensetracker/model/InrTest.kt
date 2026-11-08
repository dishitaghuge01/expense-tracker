package dev.expensetracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InrTest {
    @Test fun `statement decimals become exact integer paise`() {
        val examples = mapOf(
            "0.01" to 1L, "1" to 100L, "1.2" to 120L, "1.23" to 123L,
            "0001.20" to 120L, "1000000000" to Inr.MAX_AMOUNT_PAISE,
        )
        examples.forEach { (text, expected) -> assertEquals(expected, Inr.parseStatementAmount(text)) }
    }

    @Test fun `statement parser rejects rounding signs grouping currency and malformed input`() {
        listOf(
            "", " ", " 1", "1 ", "+1", "-1", "-0.01", "₹1", "INR 1", "Rs.1",
            "1,000", ".50", "1.", "1.001", "1.2.3", "1e2", "NaN", "Infinity",
            "１２", "١٢", "1\n", "1\u0000",
        ).forEach { text ->
            val error = assertThrows(IllegalArgumentException::class.java) { Inr.parseStatementAmount(text) }
            assertEquals("INVALID_AMOUNT_FORMAT", error.message)
        }
    }

    @Test fun `zero and over-limit values fail without amount in the error`() {
        listOf("0", "0.00", "000.0", "1000000000.01", "9223372036854775808").forEach { text ->
            val error = assertThrows(IllegalArgumentException::class.java) { Inr.parseStatementAmount(text) }
            assertEquals("AMOUNT_OUT_OF_RANGE", error.message)
        }
        listOf(Long.MIN_VALUE, -1L, 0L, Inr.MAX_AMOUNT_PAISE + 1, Long.MAX_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Inr.requireAmount(value) }
        }
    }

    @Test fun `known-template amount digits support Indian and western grouping`() {
        val examples = mapOf(
            "1,234.56" to 123_456L,
            "12,34,567.89" to 123_456_789L,
            "1,234,567.89" to 123_456_789L,
            "1,00,00,00,000.00" to Inr.MAX_AMOUNT_PAISE,
            "850" to 85_000L,
        )
        examples.forEach { (text, expected) -> assertEquals(expected, Inr.parseAlertAmountDigits(text)) }
    }

    @Test fun `alert digits reject malformed groups and commas in fractions`() {
        listOf("1,23", "1234,567", "1,,234", ",123", "01,234", "1,234,56", "1.2,3", "1,234.5,6").forEach {
            assertThrows(IllegalArgumentException::class.java) { Inr.parseAlertAmountDigits(it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Inr.parseAlertAmountDigits("Debited INR 850; balance INR 5000")
        }
    }

    @Test fun `formatting uses Indian grouping with exact signed paise`() {
        val examples = mapOf(
            0L to "₹0.00", 1L to "₹0.01", -1L to "-₹0.01", 100L to "₹1.00",
            99_999L to "₹999.99", 100_000L to "₹1,000.00",
            12_345_678L to "₹1,23,456.78", -12_345_678L to "-₹1,23,456.78",
            Inr.MAX_AMOUNT_PAISE to "₹1,00,00,00,000.00",
            Long.MAX_VALUE to "₹92,23,37,20,36,85,47,758.07",
            Long.MIN_VALUE to "-₹92,23,37,20,36,85,47,758.08",
        )
        examples.forEach { (paise, expected) -> assertEquals(expected, Inr.formatPaise(paise)) }
    }

    @Test fun `summary sum supports negative net and totals above transaction limit`() {
        assertEquals(0L, Inr.sumPaise(emptyList()))
        assertEquals(-5_000L, Inr.sumPaise(listOf(5_000L, -10_000L)))
        assertEquals(2 * Inr.MAX_AMOUNT_PAISE, Inr.sumPaise(listOf(Inr.MAX_AMOUNT_PAISE, Inr.MAX_AMOUNT_PAISE)))
        assertEquals(Long.MAX_VALUE, Inr.sumPaise(listOf(Long.MAX_VALUE)))
    }

    @Test fun `summary overflow fails instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) { Inr.sumPaise(listOf(Long.MAX_VALUE, 1L)) }
        assertThrows(ArithmeticException::class.java) { Inr.sumPaise(listOf(Long.MIN_VALUE, -1L)) }
    }
}
