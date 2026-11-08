package dev.expensetracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAccessTest {
    private val enabled = CaptureAccess(true, LeaseAccess.VALID, CaptureConsent(true, true, true))

    @Test fun `default opt-ins are all off even with granted permissions`() {
        for (source in CaptureSource.entries) {
            assertFalse(CaptureConsent().optedIn(source))
            assertEquals(CaptureState.NOT_ACTIVATED, CaptureAccess().state(source, SourcePermission.GRANTED))
            assertEquals(CaptureState.NOT_OPTED_IN, enabled.copy(consent = CaptureConsent()).state(source, SourcePermission.GRANTED))
        }
    }

    @Test fun `activation lease consent pause and permission truth table fails closed`() {
        for (registered in listOf(false, true)) {
            for (lease in LeaseAccess.entries) {
                for (optedIn in listOf(false, true)) {
                    for (paused in listOf(false, true)) {
                        for (permission in SourcePermission.entries) {
                            val access = CaptureAccess(registered, lease, CaptureConsent(optedIn, optedIn, optedIn), paused)
                            for (source in CaptureSource.entries) {
                                val allowed = registered && lease == LeaseAccess.VALID && optedIn && !paused &&
                                    permission == SourcePermission.GRANTED
                                assertEquals(allowed, access.state(source, permission) == CaptureState.ENABLED)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun `each source requires its own consent`() {
        val consents = listOf(CaptureConsent(liveSms = true), CaptureConsent(history = true), CaptureConsent(notifications = true))
        consents.forEachIndexed { index, consent ->
            CaptureSource.entries.forEachIndexed { sourceIndex, source ->
                assertEquals(index == sourceIndex, enabled.copy(consent = consent).state(source, SourcePermission.GRANTED) == CaptureState.ENABLED)
            }
        }
    }

    @Test fun `denied blocked unavailable paused and revoked lease are distinct`() {
        val source = CaptureSource.LIVE_SMS
        assertEquals(CaptureState.PERMISSION_DENIED, enabled.state(source, SourcePermission.DENIED))
        assertEquals(CaptureState.PERMISSION_BLOCKED, enabled.state(source, SourcePermission.BLOCKED))
        assertEquals(CaptureState.UNAVAILABLE, enabled.state(source, SourcePermission.UNAVAILABLE))
        assertEquals(CaptureState.PAUSED, enabled.copy(paused = true).state(source, SourcePermission.GRANTED))
        assertEquals(CaptureState.LEASE_REQUIRED, enabled.copy(lease = LeaseAccess.MISSING).state(source, SourcePermission.GRANTED))
        assertEquals(CaptureState.LEASE_EXPIRED, enabled.copy(lease = LeaseAccess.EXPIRED).state(source, SourcePermission.GRANTED))
        assertEquals(CaptureState.ACCESS_DENIED, enabled.copy(lease = LeaseAccess.ONLINE_DENIED).state(source, SourcePermission.GRANTED))
        assertEquals(CaptureState.CLOCK_UNTRUSTED, enabled.copy(lease = LeaseAccess.CLOCK_UNTRUSTED).state(source, SourcePermission.GRANTED))
    }

    @Test fun `revocation immediately stops an otherwise enabled source`() {
        assertEquals(CaptureState.ENABLED, enabled.state(CaptureSource.LIVE_SMS, SourcePermission.GRANTED))
        assertEquals(CaptureState.PERMISSION_DENIED, enabled.state(CaptureSource.LIVE_SMS, SourcePermission.DENIED))
        assertFalse(enabled.copy(registeredDevice = false).canRecoverLiveSms(SourcePermission.GRANTED, SourcePermission.GRANTED))
    }

    @Test fun `live recovery additionally requires read permission but not history-scan consent`() {
        val liveOnly = enabled.copy(consent = CaptureConsent(liveSms = true))
        assertTrue(liveOnly.canRecoverLiveSms(SourcePermission.GRANTED, SourcePermission.GRANTED))
        assertFalse(liveOnly.canRecoverLiveSms(SourcePermission.GRANTED, SourcePermission.DENIED))
        assertFalse(liveOnly.canRecoverLiveSms(SourcePermission.DENIED, SourcePermission.GRANTED))
        assertFalse(enabled.copy(consent = CaptureConsent(history = true)).canRecoverLiveSms(SourcePermission.GRANTED, SourcePermission.GRANTED))
        assertEquals(CaptureState.NOT_OPTED_IN, liveOnly.state(CaptureSource.SMS_HISTORY, SourcePermission.GRANTED))
    }
}
