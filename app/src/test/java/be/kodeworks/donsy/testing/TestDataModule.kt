// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.di.DataStoreModule
import be.kodeworks.donsy.data.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton

/** Fresh in-memory database and a throwaway preferences file for every Hilt test. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class, DataStoreModule::class])
object TestDataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DonsyDatabase =
        Room.inMemoryDatabaseBuilder(context, DonsyDatabase::class.java).build()

    @Provides
    @Singleton
    fun providePreferencesDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File.createTempFile("test", ".preferences_pb").apply { delete() }
        }
}
