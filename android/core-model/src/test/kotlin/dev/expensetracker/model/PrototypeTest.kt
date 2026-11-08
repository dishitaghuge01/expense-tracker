package dev.expensetracker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PrototypeTest {
    @Test fun `synthetic total uses integer paise and separates refund`() {
        assertEquals(109_000L, SyntheticDemo.grossPaise)
        assertEquals(5_000L, SyntheticDemo.refundPaise)
        assertEquals(104_000L, SyntheticDemo.netPaise)
    }
}
