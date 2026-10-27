// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.di

import android.content.Context
import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.db.dao.BrainDumpDao
import be.kodeworks.donsy.data.db.dao.EnergyCheckInDao
import be.kodeworks.donsy.data.db.dao.FocusSessionDao
import be.kodeworks.donsy.data.db.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Only the database itself, so tests can swap in an in-memory one and keep [DaoModule]. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DonsyDatabase =
        Room.databaseBuilder(context, DonsyDatabase::class.java, DonsyDatabase.NAME).build()
}

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideBrainDumpDao(database: DonsyDatabase): BrainDumpDao = database.brainDumpDao()

    @Provides
    fun provideTaskDao(database: DonsyDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideFocusSessionDao(database: DonsyDatabase): FocusSessionDao = database.focusSessionDao()

    @Provides
    fun provideEnergyCheckInDao(database: DonsyDatabase): EnergyCheckInDao = database.energyCheckInDao()
}
