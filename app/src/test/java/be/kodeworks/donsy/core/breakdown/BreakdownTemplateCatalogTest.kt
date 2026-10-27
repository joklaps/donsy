// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.breakdown

import be.kodeworks.donsy.domain.breakdown.BreakdownEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Guards the template resources: translators edit them, and a broken keyword list fails silently. */
@RunWith(RobolectricTestRunner::class)
class BreakdownTemplateCatalogTest {

    private val templates get() = BreakdownTemplateCatalog.load(RuntimeEnvironment.getApplication().resources)

    private fun bestMatch(title: String) = BreakdownEngine.suggest(title, "", templates).firstOrNull()?.id

    @Test
    fun everyTemplateHasANameKeywordsAndSteps() {
        assertEquals(templates.size, templates.map { it.id }.toSet().size)
        templates.forEach { template ->
            assertTrue(template.id, template.name.isNotBlank())
            assertTrue(template.id, template.keywords.isNotEmpty() && template.keywords.none { it.isBlank() })
            assertTrue(template.id, template.steps.size >= 3 && template.steps.none { it.isBlank() })
        }
    }

    @Test
    fun englishTitlesFindTheirTemplate() {
        assertEquals("study_test", bestMatch("Prepare for my biology test"))
        assertEquals("write_text", bestMatch("History essay"))
        assertEquals("presentation", bestMatch("Presentation about volcanoes"))
        assertEquals("homework", bestMatch("Maths homework"))
        assertEquals("tidy_room", bestMatch("Clean my bedroom"))
        assertEquals("call", bestMatch("Make an appointment at the dentist"))
        assertEquals("trip", bestMatch("Pack for the school trip"))
        assertEquals("read_book", bestMatch("Read chapter 4"))
        assertEquals(null, bestMatch("Water the plants"))
    }

    @Test
    @Config(qualifiers = "nl")
    fun dutchTitlesFindTheirTemplate() {
        assertEquals("study_test", bestMatch("Biologietoets"))
        assertEquals("write_text", bestMatch("Verslag geschiedenis"))
        assertEquals("presentation", bestMatch("Spreekbeurt over vulkanen"))
        assertEquals("homework", bestMatch("Huiswerk wiskunde"))
        assertEquals("tidy_room", bestMatch("Slaapkamer opruimen"))
        assertEquals("call", bestMatch("Afspraak bij de tandarts"))
        assertEquals("trip", bestMatch("Inpakken voor schoolreis"))
        assertEquals("read_book", bestMatch("Hoofdstuk 4 lezen"))
        assertEquals(null, bestMatch("De planten water geven"))
    }
}
