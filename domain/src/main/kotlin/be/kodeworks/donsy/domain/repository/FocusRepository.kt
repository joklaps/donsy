// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.repository

import be.kodeworks.donsy.domain.focus.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusRepository {
    /** The running or paused session, if any. There is at most one. */
    fun observeActive(): Flow<FocusSession?>

    suspend fun getActive(): FocusSession?

    suspend fun get(id: Long): FocusSession?

    /** Stores a new session and returns its id. */
    suspend fun insert(session: FocusSession): Long

    suspend fun update(session: FocusSession)
}
