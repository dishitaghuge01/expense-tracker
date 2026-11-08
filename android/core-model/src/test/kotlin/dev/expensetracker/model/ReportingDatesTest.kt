package dev.expensetracker.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReportingDatesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-08T18:30:00Z"), ZoneOffset.UTC)

    @Test fun `dates and months are strict ISO including leap-year validation`() {
        assertEquals(LocalDate.of(2024, 2, 29), ReportingDates.parseDate("2024-02-29"))
        assertEquals(YearMonth.of(2026, 11), ReportingDates.parseMonth("2026-11"))
        listOf("2026-02-29", "2026-04-31", "2026-13-01", "2026-1-01", "08/11/2026", "0000-01-01", "2026-11-08 ").forEach {
            val error = assertThrows(IllegalArgumentException::class.java) { ReportingDates.parseDate(it) }
            assertEquals("INVALID_DATE", error.message)
        }
        listOf("2026-1", "2026-00", "2026-13", "2026-11-01", "0000-01", "+2026-11").forEach {
            val error = assertThrows(IllegalArgumentException::class.java) { ReportingDates.parseMonth(it) }
            assertEquals("INVALID_MONTH", error.message)
        }
    }

    @Test fun `UTC RFC3339 allows Z and explicit zero offset with exact fractions`() {
        val expected = Instant.parse("2026-11-08T01:02:03.123456789Z")
        assertEquals(expected, ReportingDates.parseUtcInstant("2026-11-08T01:02:03.123456789Z"))
        assertEquals(expected, ReportingDates.parseUtcInstant("2026-11-08T01:02:03.123456789+00:00"))
    }

    @Test fun `invalid ambiguous or non-UTC instants are not normalized silently`() {
        listOf(
            "2026-11-08", "2026-11-08T01:02:03", "2026-11-08T01:02:03+05:30",
            "2026-11-08T01:02:03-00:00", "2026-02-30T01:02:03Z", "2026-11-08T24:00:00Z",
            "2026-11-08T01:02:60Z", "2026-11-08T01:02:03Z\n", "2026-1-08T01:02:03Z",
        ).forEach {
            val error = assertThrows(IllegalArgumentException::class.java) { ReportingDates.parseUtcInstant(it) }
            assertEquals("INVALID_UTC_INSTANT", error.message)
        }
    }

    @Test fun `IST midnight changes reporting day and month independently of UTC`() {
        assertEquals(LocalDate.of(2026, 10, 31), ReportingDates.reportDate(Instant.parse("2026-10-31T18:29:59.999999999Z")))
        val nextDay = ReportingDates.reportDate(Instant.parse("2026-10-31T18:30:00Z"))
        assertEquals(LocalDate.of(2026, 11, 1), nextDay)
        assertEquals(YearMonth.of(2026, 11), ReportingDates.reportMonth(nextDay))
    }

    @Test fun `reporting today ignores host timezone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(LocalDate.of(2026, 11, 9), ReportingDates.today(clock))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun `statement coverage is inclusive and date-only`() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 9)
        assertEquals(from, ReportingDates.parseStatementDate("2026-11-01", from, to, clock))
        assertEquals(to, ReportingDates.parseStatementDate("2026-11-09", from, to, clock))
    }

    @Test fun `future and out-of-coverage statement rows are rejected`() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        assertEquals("FUTURE_STATEMENT_DATE", assertThrows(IllegalArgumentException::class.java) {
            ReportingDates.parseStatementDate("2026-11-10", from, to, clock)
        }.message)
        assertEquals("DATE_OUTSIDE_COVERAGE", assertThrows(IllegalArgumentException::class.java) {
            ReportingDates.parseStatementDate("2026-10-31", from, to, clock)
        }.message)
        assertEquals("INVALID_COVERAGE_RANGE", assertThrows(IllegalArgumentException::class.java) {
            ReportingDates.parseStatementDate("2026-11-01", to, from, clock)
        }.message)
    }

    @Test fun `calendar weeks start Monday across month and year boundaries`() {
        assertEquals(LocalDate.of(2026, 11, 2), ReportingDates.weekStart(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 2), ReportingDates.weekStart(LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 12, 28), ReportingDates.weekStart(LocalDate.of(2027, 1, 1)))
    }
}
