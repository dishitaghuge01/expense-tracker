package dev.expensetracker.model

/** These opt-ins are independent; a history scan must never inherit live-SMS consent. */
enum class CaptureSource { LIVE_SMS, SMS_HISTORY, NOTIFICATIONS }
enum class SourcePermission { GRANTED, DENIED, BLOCKED, UNAVAILABLE }

/** Supplied only after the signed lease and its device/account binding have been verified. */
enum class LeaseAccess { VALID, MISSING, EXPIRED, ONLINE_DENIED, CLOCK_UNTRUSTED }

enum class CaptureState {
    NOT_ACTIVATED, LEASE_REQUIRED, LEASE_EXPIRED, ACCESS_DENIED, CLOCK_UNTRUSTED,
    NOT_OPTED_IN, PAUSED, PERMISSION_DENIED, PERMISSION_BLOCKED, UNAVAILABLE, ENABLED,
}

data class CaptureConsent(
    val liveSms: Boolean = false,
    val history: Boolean = false,
    val notifications: Boolean = false,
) {
    fun optedIn(source: CaptureSource): Boolean = when (source) {
        CaptureSource.LIVE_SMS -> liveSms
        CaptureSource.SMS_HISTORY -> history
        CaptureSource.NOTIFICATIONS -> notifications
    }
}

data class CaptureAccess(
    val registeredDevice: Boolean = false,
    val lease: LeaseAccess = LeaseAccess.MISSING,
    val consent: CaptureConsent = CaptureConsent(),
    val paused: Boolean = false,
) {
    /** Permission grants alone, including grants from the compatibility probe, cannot activate capture. */
    fun state(source: CaptureSource, permission: SourcePermission): CaptureState {
        if (!registeredDevice) return CaptureState.NOT_ACTIVATED
        when (lease) {
            LeaseAccess.MISSING -> return CaptureState.LEASE_REQUIRED
            LeaseAccess.EXPIRED -> return CaptureState.LEASE_EXPIRED
            LeaseAccess.ONLINE_DENIED -> return CaptureState.ACCESS_DENIED
            LeaseAccess.CLOCK_UNTRUSTED -> return CaptureState.CLOCK_UNTRUSTED
            LeaseAccess.VALID -> Unit
        }
        if (!consent.optedIn(source)) return CaptureState.NOT_OPTED_IN
        if (paused) return CaptureState.PAUSED
        return when (permission) {
            SourcePermission.GRANTED -> CaptureState.ENABLED
            SourcePermission.DENIED -> CaptureState.PERMISSION_DENIED
            SourcePermission.BLOCKED -> CaptureState.PERMISSION_BLOCKED
            SourcePermission.UNAVAILABLE -> CaptureState.UNAVAILABLE
        }
    }

    /** Inbox recovery is separate from broadcasts and additionally requires READ_SMS. */
    fun canRecoverLiveSms(receiveSms: SourcePermission, readSms: SourcePermission): Boolean =
        state(CaptureSource.LIVE_SMS, receiveSms) == CaptureState.ENABLED && readSms == SourcePermission.GRANTED
}
