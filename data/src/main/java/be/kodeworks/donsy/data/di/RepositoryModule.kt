// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.di

import be.kodeworks.donsy.data.backup.RoomBackupRepository
import be.kodeworks.donsy.data.repository.RoomBrainDumpRepository
import be.kodeworks.donsy.data.repository.RoomEnergyRepository
import be.kodeworks.donsy.data.repository.RoomFocusRepository
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.backup.BackupRepository
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.repository.EnergyRepository
import be.kodeworks.donsy.domain.repository.FocusRepository
import be.kodeworks.donsy.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBrainDumpRepository(impl: RoomBrainDumpRepository): BrainDumpRepository

    @Binds
    abstract fun bindTaskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    abstract fun bindFocusRepository(impl: RoomFocusRepository): FocusRepository

    @Binds
    abstract fun bindEnergyRepository(impl: RoomEnergyRepository): EnergyRepository

    @Binds
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository
}
