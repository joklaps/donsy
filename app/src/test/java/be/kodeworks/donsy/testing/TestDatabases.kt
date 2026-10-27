// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import org.robolectric.RuntimeEnvironment
import java.io.File

/** In-memory database for Robolectric tests; close it in @After. */
fun inMemoryDatabase(): DonsyDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java).build()

/** Preferences in a throwaway file. */
fun testPreferences(): UserPreferencesRepository = UserPreferencesRepository(
    PreferenceDataStoreFactory.create { File.createTempFile("test", ".preferences_pb").apply { delete() } },
)
