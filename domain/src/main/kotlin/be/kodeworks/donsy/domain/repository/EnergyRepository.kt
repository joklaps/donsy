// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.repository

import be.kodeworks.donsy.domain.model.EnergyCheckIn
import be.kodeworks.donsy.domain.model.EnergyLevel
import kotlinx.coroutines.flow.Flow

interface EnergyRepository {
    /** The most recent check-in, however old; see `EnergyCheckIns.current`. */
    fun observeLatest(): Flow<EnergyCheckIn?>

    /** Records a check-in now and returns its id. */
    suspend fun checkIn(level: EnergyLevel): Long

    /** Takes back a check-in, e.g. when the user deselects their answer. */
    suspend fun delete(id: Long)
}
