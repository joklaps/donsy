// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import be.kodeworks.donsy.data.db.dao.FocusSessionDao
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import be.kodeworks.donsy.domain.focus.FocusSession
import be.kodeworks.donsy.domain.repository.FocusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

class RoomFocusRepository @Inject constructor(
    private val dao: FocusSessionDao,
) : FocusRepository {

    override fun observeActive(): Flow<FocusSession?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun getActive(): FocusSession? = dao.getActive()?.toDomain()

    override suspend fun get(id: Long): FocusSession? = dao.get(id)?.toDomain()

    override suspend fun insert(session: FocusSession): Long = dao.insert(session.toEntity())

    override suspend fun update(session: FocusSession) = dao.update(session.toEntity())
}

private fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    taskId = taskId,
    subtaskId = subtaskId,
    planned = Duration.ofMillis(plannedDurationMillis),
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    accumulatedRun = Duration.ofMillis(accumulatedRunMillis),
    lastResumedAtWall = lastResumedAtWall,
    lastResumedAtElapsedMillis = lastResumedAtElapsedMillis,
    bootCount = bootCount,
    pausedAt = pausedAt,
)

private fun FocusSession.toEntity() = FocusSessionEntity(
    id = id,
    taskId = taskId,
    subtaskId = subtaskId,
    plannedDurationMillis = planned.toMillis(),
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    accumulatedRunMillis = accumulatedRun.toMillis(),
    lastResumedAtWall = lastResumedAtWall,
    lastResumedAtElapsedMillis = lastResumedAtElapsedMillis,
    bootCount = bootCount,
    pausedAt = pausedAt,
)
