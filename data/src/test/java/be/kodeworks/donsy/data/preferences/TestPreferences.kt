// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files

/** Preferences in a fresh temporary file, for tests that need them only on the side (e.g. [DataPurger]). */
fun testPreferences(): UserPreferencesRepository {
    val file = File(Files.createTempDirectory("prefs").toFile(), "test.preferences_pb").apply { deleteOnExit() }
    return UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file },
    )
}
