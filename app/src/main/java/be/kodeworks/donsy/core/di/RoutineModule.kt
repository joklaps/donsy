// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.di

import be.kodeworks.donsy.core.routine.AndroidReminderPermissions
import be.kodeworks.donsy.core.routine.AndroidRoutineAlarmScheduler
import be.kodeworks.donsy.core.routine.AndroidRoutineNotifier
import be.kodeworks.donsy.core.routine.ReminderPermissions
import be.kodeworks.donsy.core.routine.RoutineAlarmScheduler
import be.kodeworks.donsy.core.routine.RoutineNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RoutineModule {

    @Binds
    abstract fun bindPermissions(impl: AndroidReminderPermissions): ReminderPermissions

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidRoutineAlarmScheduler): RoutineAlarmScheduler

    @Binds
    abstract fun bindNotifier(impl: AndroidRoutineNotifier): RoutineNotifier
}
