// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class RepeatDaysTest {

    @Test
    fun everyDayAndWeekdaysAreRecognisedWhateverTheOrder() {
        assertTrue(RepeatDays.of(DayOfWeek.entries.reversed())!!.isEveryDay)
        val weekdays = RepeatDays.of(listOf(FRIDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY))!!
        assertTrue(weekdays.isWeekdays)
        assertEquals(RepeatDays.WEEKDAYS, weekdays)
        assertFalse(SATURDAY in weekdays)
    }

    @Test
    fun pickedDaysRoundTripThroughTheirBits() {
        val days = RepeatDays.of(setOf(SUNDAY, WEDNESDAY))!!
        assertEquals(listOf(WEDNESDAY, SUNDAY), days.days)
        assertEquals(days, RepeatDays.fromBits(days.bits))
        assertFalse(days.isEveryDay || days.isWeekdays)
    }

    @Test
    fun noDaysIsNoRepeat() {
        assertNull(RepeatDays.of(emptySet()))
        assertNull(RepeatDays.fromBits(0))
        assertNull(RepeatDays.fromBits(null))
        assertNull(RepeatDays.fromBits(0b1000_0000))
    }
}
