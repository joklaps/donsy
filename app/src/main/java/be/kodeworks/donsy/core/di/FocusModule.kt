// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.di

import be.kodeworks.donsy.core.focus.AndroidFocusAlarmScheduler
import be.kodeworks.donsy.core.focus.AndroidFocusNotifier
import be.kodeworks.donsy.core.focus.FocusAlarmScheduler
import be.kodeworks.donsy.core.focus.FocusNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FocusModule {

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidFocusAlarmScheduler): FocusAlarmScheduler

    @Binds
    abstract fun bindNotifier(impl: AndroidFocusNotifier): FocusNotifier
}
