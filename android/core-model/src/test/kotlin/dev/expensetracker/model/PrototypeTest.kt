package dev.expensetracker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PrototypeTest {
    @Test fun `permission state truth table never claims grant from rationale alone`() {
        for (granted in listOf(false, true)) {
            for (requested in listOf(false, true)) {
                for (rationale in listOf(false, true)) {
                    val expected = when {
                        granted -> ProbePermissionState.GRANTED
                        requested && !rationale -> ProbePermissionState.BLOCKED
                        else -> ProbePermissionState.NOT_GRANTED
                    }
                    assertEquals(expected, probePermissionState(granted, requested, rationale))
                }
            }
        }
    }

    @Test fun `revocation removes granted status`() {
        assertEquals(ProbePermissionState.GRANTED, probePermissionState(true, true, false))
        assertEquals(ProbePermissionState.NOT_GRANTED, probePermissionState(false, true, true))
    }

    @Test fun `synthetic total uses integer paise and separates refund`() {
        assertEquals(109_000L, SyntheticDemo.grossPaise)
        assertEquals(5_000L, SyntheticDemo.refundPaise)
        assertEquals(104_000L, SyntheticDemo.netPaise)
    }
}
