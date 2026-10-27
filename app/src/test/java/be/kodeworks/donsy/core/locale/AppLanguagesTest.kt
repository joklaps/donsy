// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLanguagesTest {

    @Test
    fun supportedLanguagesComeFromTheGeneratedLocaleConfig() {
        val tags = AppLanguages.supportedTags(RuntimeEnvironment.getApplication())
        assertTrue("expected en and nl in $tags", tags.containsAll(listOf("en", "nl")))
    }

    @Test
    fun languagesAreNamedInTheirOwnLanguage() {
        assertEquals("Nederlands", AppLanguages.nativeDisplayName("nl"))
        assertEquals("English", AppLanguages.nativeDisplayName("en"))
    }
}
