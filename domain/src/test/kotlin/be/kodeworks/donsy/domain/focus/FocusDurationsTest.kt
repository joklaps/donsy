// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusDurationsTest {

    @Test
    fun nothingStoredOffersTheDefaults() {
        assertEquals(listOf(5, 10, 15, 25), FocusDurations.choices(null))
        assertEquals(15, FocusDurations.defaultMinutes(null, FocusDurations.DEFAULT_CHOICES))
    }

    @Test
    fun storedChoicesAreSortedAndUnknownValuesDropped() {
        assertEquals(listOf(10, 45), FocusDurations.choices(setOf(45, 7, 10)))
        assertEquals(FocusDurations.DEFAULT_CHOICES, FocusDurations.choices(setOf(7)))
    }

    @Test
    fun anEstimatePicksTheShortestLengthThatCoversIt() {
        val choices = listOf(5, 10, 15, 25)
        assertEquals(5, FocusDurations.forEstimate(4, choices))
        assertEquals(15, FocusDurations.forEstimate(15, choices))
        assertEquals(25, FocusDurations.forEstimate(20, choices))
    }

    @Test
    fun anEstimateLongerThanEveryLengthPicksTheLongest() {
        assertEquals(25, FocusDurations.forEstimate(60, listOf(5, 10, 15, 25)))
    }

    @Test
    fun theStoredDefaultIsUsedWhileItIsOffered() {
        assertEquals(25, FocusDurations.defaultMinutes(25, listOf(10, 25)))
    }

    @Test
    fun otherwiseTheOfferedLengthClosestTo15MinutesIsPreselected() {
        assertEquals(20, FocusDurations.defaultMinutes(25, listOf(5, 20, 45)))
        // 10 and 20 are equally close: the shorter one wins.
        assertEquals(10, FocusDurations.defaultMinutes(null, listOf(10, 20)))
        assertEquals(60, FocusDurations.defaultMinutes(null, listOf(60)))
    }
}
