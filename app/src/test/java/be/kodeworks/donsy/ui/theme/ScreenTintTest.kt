// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTintTest {

    @Test
    fun everyTabGetsItsOwnAccentInLightAndDark() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { scheme ->
            val accents = ScreenTint.entries.map { scheme.tabAccent(it) }
            assertEquals(ScreenTint.entries.size, accents.toSet().size)
        }
    }
}
