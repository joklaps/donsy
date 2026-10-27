// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import be.kodeworks.donsy.data.db.dao.EnergyCheckInDao
import be.kodeworks.donsy.data.db.entity.EnergyCheckInEntity
import be.kodeworks.donsy.domain.model.EnergyCheckIn
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.repository.EnergyRepository
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomEnergyRepository @Inject constructor(
    private val dao: EnergyCheckInDao,
    private val timeSource: TimeSource,
) : EnergyRepository {

    override fun observeLatest(): Flow<EnergyCheckIn?> =
        dao.observeLatest().map { it?.let { entity -> EnergyCheckIn(entity.id, entity.level, entity.checkedInAt) } }

    override suspend fun checkIn(level: EnergyLevel): Long =
        dao.insert(EnergyCheckInEntity(level = level, checkedInAt = timeSource.now()))

    override suspend fun delete(id: Long) = dao.delete(id)
}
