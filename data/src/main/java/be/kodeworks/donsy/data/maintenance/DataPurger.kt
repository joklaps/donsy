// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.maintenance

import be.kodeworks.donsy.data.db.dao.BrainDumpDao
import be.kodeworks.donsy.data.db.dao.EnergyCheckInDao
import be.kodeworks.donsy.data.db.dao.TaskDao
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.archive.AutoArchive
import be.kodeworks.donsy.domain.energy.EnergyCheckIns
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject

/**
 * Runs on app start. Permanently removes items that were soft-deleted longer ago than [RETENTION],
 * and energy check-ins older than [EnergyCheckIns.RETENTION]: only the latest one is ever used.
 * Also archives done tasks that were left alone for the chosen number of days ([AutoArchive]).
 */
class DataPurger @Inject constructor(
    private val brainDumpDao: BrainDumpDao,
    private val taskDao: TaskDao,
    private val energyCheckInDao: EnergyCheckInDao,
    private val preferences: UserPreferencesRepository,
    private val timeSource: TimeSource,
) {
    suspend fun purge() {
        val now = timeSource.now()
        val cutoff = now.minus(RETENTION)
        brainDumpDao.purgeDeletedBefore(cutoff)
        taskDao.purgeDeletedBefore(cutoff)
        energyCheckInDao.deleteBefore(now.minus(EnergyCheckIns.RETENTION))
        AutoArchive.cutoff(preferences.archiveAfterDays.first(), now)?.let { taskDao.archiveDoneBefore(it, now) }
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
