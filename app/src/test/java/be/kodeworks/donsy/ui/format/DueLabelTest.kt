// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DueLabelTest {

    private val today = LocalDate.of(2026, 10, 21) // a Wednesday
    private val now = today.atTime(12, 0)

    private fun label(due: LocalDate, time: LocalTime? = null) = DueLabel.of(due, time, now)

    @Test
    fun nearDates() {
        assertEquals(DueLabel.Today(), label(today))
        assertEquals(DueLabel.Tomorrow(), label(today.plusDays(1)))
        assertEquals(DueLabel.ThisWeek(today.plusDays(2)), label(today.plusDays(2)))
        assertEquals(DueLabel.ThisWeek(today.plusDays(6)), label(today.plusDays(6)))
    }

    @Test
    fun laterDatesKnowWhetherTheyAreThisYear() {
        assertEquals(DueLabel.Later(today.plusDays(7), isThisYear = true), label(today.plusDays(7)))
        assertEquals(DueLabel.Later(LocalDate.of(2027, 1, 5), isThisYear = false), label(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun pastDatesAreNeutralAndUseWeekdaysWithinAWeek() {
        val yesterday = label(today.minusDays(1))
        assertTrue(yesterday.isPast)
        assertEquals(DueLabel.Past(today.minusDays(1), withinWeek = true, isThisYear = true), yesterday)
        assertEquals(DueLabel.Past(today.minusDays(7), withinWeek = false, isThisYear = true), label(today.minusDays(7)))
        assertFalse(label(today).isPast)
    }

    @Test
    fun aChosenTimeIsKept() {
        val four = LocalTime.of(16, 0)
        assertEquals(DueLabel.Today(four), label(today, four))
        assertEquals(DueLabel.Tomorrow(four), label(today.plusDays(1), four))
        assertEquals(DueLabel.ThisWeek(today.plusDays(3), four), label(today.plusDays(3), four))
        assertEquals(DueLabel.Past(today.minusDays(1), withinWeek = true, isThisYear = true, four), label(today.minusDays(1), four))
    }

    @Test
    fun onlyAChosenTimeCanPassToday() {
        val nine = LocalTime.of(9, 0)
        assertEquals(DueLabel.EarlierToday(nine), label(today, nine))
        assertTrue(label(today, nine).isPast)
        // Without a time the task is due by the end of the day.
        assertFalse(DueLabel.of(today, null, today.atTime(23, 58)).isPast)
        assertFalse(label(today, LocalTime.of(12, 0)).isPast)
    }
}
