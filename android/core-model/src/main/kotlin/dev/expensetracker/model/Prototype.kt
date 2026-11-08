package dev.expensetracker.model

/** No capture path exists in the disposable permission-only prototype. */
enum class ProbePermissionState(val label: String) {
    NOT_CHECKED("Not checked — permission probe not started"),
    GRANTED("Granted — capture remains disabled"),
    NOT_GRANTED("Not granted — denied, restricted or unavailable"),
    BLOCKED("Blocked — change may require Android settings"),
}

object SyntheticDemo {
    // Fictional, static values. Never derived from alerts or device data.
    val expensesPaise: List<Long> = listOf(85_000L, 24_000L)
    const val refundPaise: Long = 5_000L
    val grossPaise: Long get() = expensesPaise.sum()
    val netPaise: Long get() = grossPaise - refundPaise
}
