// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.maintenance.DataPurger
import be.kodeworks.donsy.data.preferences.testPreferences
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.time.FakeTimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RoomEnergyRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = FakeTimeSource()
    private val energy = RoomEnergyRepository(database.energyCheckInDao(), time)
    private val purger = DataPurger(database.brainDumpDao(), database.taskDao(), database.energyCheckInDao(), testPreferences(), time)

    @After
    fun tearDown() = database.close()

    @Test
    fun theLatestCheckInWins() = runTest {
        assertNull(energy.observeLatest().first())

        energy.checkIn(EnergyLevel.LOW)
        time.advanceBy(Duration.ofMinutes(5))
        energy.checkIn(EnergyLevel.HIGH)

        with(energy.observeLatest().first()!!) {
            assertEquals(EnergyLevel.HIGH, level)
            assertEquals(time.now, checkedInAt)
        }
    }

    @Test
    fun deletingTheLatestFallsBackToTheOneBefore() = runTest {
        energy.checkIn(EnergyLevel.LOW)
        time.advanceBy(Duration.ofMinutes(5))
        val latest = energy.checkIn(EnergyLevel.HIGH)

        energy.delete(latest)

        assertEquals(EnergyLevel.LOW, energy.observeLatest().first()?.level)
    }

    @Test
    fun purgeRemovesCheckInsOlderThan30Days() = runTest {
        energy.checkIn(EnergyLevel.MEDIUM)
        time.advanceBy(Duration.ofDays(30).plusMinutes(1))

        purger.purge()

        assertNull(energy.observeLatest().first())
    }
}
