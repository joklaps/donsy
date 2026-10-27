// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.energy

import be.kodeworks.donsy.domain.model.EnergyCheckIn
import be.kodeworks.donsy.domain.model.EnergyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EnergyCheckInsTest {

    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private fun checkInAgo(age: Duration) = EnergyCheckIn(1, EnergyLevel.LOW, now.minus(age))

    @Test
    fun noCheckInMeansNoEnergy() {
        assertNull(EnergyCheckIns.current(null, now))
    }

    @Test
    fun aRecentCheckInCounts() {
        assertEquals(EnergyLevel.LOW, EnergyCheckIns.current(checkInAgo(Duration.ofHours(3).plusMinutes(59)), now))
    }

    @Test
    fun aCheckInExpiresAfterFourHours() {
        assertNull(EnergyCheckIns.current(checkInAgo(Duration.ofHours(4)), now))
    }

    @Test
    fun aCheckInAfterTheClockMovedBackStillCounts() {
        assertEquals(EnergyLevel.LOW, EnergyCheckIns.current(checkInAgo(Duration.ofMinutes(-30)), now))
    }
}
