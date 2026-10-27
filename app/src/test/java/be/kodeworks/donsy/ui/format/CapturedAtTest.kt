// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CapturedAtTest {

    private val brussels = ZoneId.of("Europe/Brussels")
    private val now = Instant.parse("2026-10-21T10:00:00Z") // 12:00 in Brussels

    @Test
    fun sameLocalDayIsToday() {
        val result = CapturedAt.of(Instant.parse("2026-10-20T22:30:00Z"), now, brussels) // 00:30 local
        assertEquals(CapturedAt(CapturedAt.Day.Today, LocalTime.of(0, 30)), result)
    }

    @Test
    fun previousLocalDayIsYesterday() {
        val result = CapturedAt.of(Instant.parse("2026-10-20T21:59:00Z"), now, brussels) // 23:59 local
        assertEquals(CapturedAt.Day.Yesterday, result.day)
    }

    @Test
    fun olderDatesCarryTheDateAndWhetherItIsThisYear() {
        assertEquals(
            CapturedAt.Day.Earlier(LocalDate.of(2026, 10, 3), isThisYear = true),
            CapturedAt.of(Instant.parse("2026-10-03T08:00:00Z"), now, brussels).day,
        )
        assertEquals(
            CapturedAt.Day.Earlier(LocalDate.of(2025, 12, 31), isThisYear = false),
            CapturedAt.of(Instant.parse("2025-12-31T08:00:00Z"), now, brussels).day,
        )
    }
}
