// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.licenses

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseParagraphsTest {

    @Test
    fun linesOfAParagraphAreJoinedSoTheyWrapToTheScreen() {
        val text = "  0. Definitions.\n\n  \"This License\" refers to version 3\nof the GNU General Public License.\n   \n  Next.\n"

        assertEquals(
            listOf("0. Definitions.", "\"This License\" refers to version 3 of the GNU General Public License.", "Next."),
            licenseParagraphs(text),
        )
    }
}
