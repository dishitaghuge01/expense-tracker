package dev.expensetracker.model

/** INR conversion without floating point, rounding, or locale-dependent parsing. */
object Inr {
    const val MAX_AMOUNT_PAISE: Long = 100_000_000_000L

    private val decimal = Regex("[0-9]+(?:\\.[0-9]{1,2})?")
    private val indianGrouped = Regex("[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3}")
    private val westernGrouped = Regex("[1-9][0-9]{0,2}(?:,[0-9]{3})+")

    /** Statement contract: positive decimal, no sign, whitespace, symbol or grouping. */
    fun parseStatementAmount(value: String): Long {
        require(decimal.matches(value)) { "INVALID_AMOUNT_FORMAT" }
        val whole = value.substringBefore('.').trimStart('0').ifEmpty { "0" }
        val fraction = value.substringAfter('.', "").padEnd(2, '0')
        val paise = (whole + fraction).toLongOrNull()
            ?: throw IllegalArgumentException("AMOUNT_OUT_OF_RANGE")
        return requireAmount(paise)
    }

    /**
     * Digits extracted by an already-matched alert template, not a free-text SMS parser.
     * Currency markers must be handled by that template. Never selects a balance/amount.
     */
    fun parseAlertAmountDigits(value: String): Long {
        val whole = value.substringBefore('.')
        require(',' !in value.substringAfter('.', "")) { "INVALID_AMOUNT_FORMAT" }
        if (',' in whole) {
            require(indianGrouped.matches(whole) || westernGrouped.matches(whole)) {
                "INVALID_AMOUNT_FORMAT"
            }
        }
        return parseStatementAmount(value.replace(",", ""))
    }

    /** Transaction and budget bounds; summaries are not constrained to this range. */
    fun requireAmount(paise: Long): Long {
        require(paise in 1L..MAX_AMOUNT_PAISE) { "AMOUNT_OUT_OF_RANGE" }
        return paise
    }

    /** Checked signed 64-bit accumulation; never silently wraps on overflow. */
    fun sumPaise(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)

    /** Indian grouping for signed summary values, including Long.MIN_VALUE. */
    fun formatPaise(paise: Long): String {
        val digits = paise.toString().removePrefix("-").padStart(3, '0')
        val rupees = digits.dropLast(2)
        val grouped = if (rupees.length <= 3) {
            rupees
        } else {
            val prefix = rupees.dropLast(3)
            val firstLength = if (prefix.length % 2 == 0) 2 else 1
            val groups = listOf(prefix.take(firstLength)) + prefix.drop(firstLength).chunked(2)
            groups.joinToString(",") + "," + rupees.takeLast(3)
        }
        return (if (paise < 0) "-" else "") + "₹" + grouped + "." + digits.takeLast(2)
    }
}
