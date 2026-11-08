package dev.expensetracker.model

import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Strict external date formats and Asia/Kolkata reporting boundaries. */
object ReportingDates {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val monthPattern = Regex("[0-9]{4}-[0-9]{2}")
    private val instantPattern = Regex(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:Z|\\+00:00)",
    )

    fun parseDate(value: String): LocalDate = validated("INVALID_DATE") {
        require(datePattern.matches(value))
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).also { require(it.year >= 1) }
    }

    fun parseMonth(value: String): YearMonth = validated("INVALID_MONTH") {
        require(monthPattern.matches(value))
        YearMonth.parse(value).also { require(it.year >= 1) }
    }

    /** Accept UTC RFC 3339 only, without silently converting a non-UTC input offset. */
    fun parseUtcInstant(value: String): Instant = validated("INVALID_UTC_INSTANT") {
        require(instantPattern.matches(value))
        val parsed = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        require(parsed.year >= 1 && parsed.offset == ZoneOffset.UTC)
        parsed.toInstant()
    }

    fun reportDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(zone))

    /** Date-only imports remain dates; this function never invents an event instant. */
    fun parseStatementDate(
        value: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        clock: Clock = Clock.systemUTC(),
    ): LocalDate {
        require(!fromDate.isAfter(toDate)) { "INVALID_COVERAGE_RANGE" }
        val date = parseDate(value)
        require(!date.isAfter(today(clock))) { "FUTURE_STATEMENT_DATE" }
        require(!date.isBefore(fromDate) && !date.isAfter(toDate)) { "DATE_OUTSIDE_COVERAGE" }
        return date
    }

    fun reportMonth(date: LocalDate): YearMonth = YearMonth.from(date)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // java.time exceptions may include raw input. Expose bounded codes without that payload.
    private inline fun <T> validated(code: String, parse: () -> T): T = try {
        parse()
    } catch (_: DateTimeException) {
        throw IllegalArgumentException(code)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(code)
    }
}
