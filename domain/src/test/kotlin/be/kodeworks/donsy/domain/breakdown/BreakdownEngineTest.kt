// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.breakdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakdownEngineTest {

    private val studyTest = BreakdownTemplate(
        id = "study_test",
        name = "Study for a test",
        keywords = listOf("test", "tests", "exam*", "*toets*", "learn for"),
        steps = listOf("Find the topics", "Gather your notes", "Review the first topic"),
    )
    private val tidyRoom = BreakdownTemplate(
        id = "tidy_room",
        name = "Tidy up a room",
        keywords = listOf("tidy*", "*room", "clean*"),
        steps = listOf("Put trash in a bag", "Clear one surface"),
    )
    private val templates = listOf(studyTest, tidyRoom)

    private fun suggest(title: String, notes: String = "") = BreakdownEngine.suggest(title, notes, templates).map { it.id }

    @Test
    fun matchesWholeWordsIgnoringCaseAndAccents() {
        assertEquals(listOf("study_test"), suggest("Prepare for my biology TEST"))
        assertEquals(listOf("study_test"), suggest("Exámen geschiedenis"))
    }

    @Test
    fun aKeywordWithoutWildcardDoesNotMatchInsideAnotherWord() {
        assertEquals(emptyList<String>(), suggest("Sign the protest letter"))
        assertEquals(emptyList<String>(), suggest("Testing the bike lights"))
    }

    @Test
    fun wildcardsMatchWordStartsEndsAndCompounds() {
        assertEquals(listOf("study_test"), suggest("Exams on Friday"))
        assertEquals(listOf("tidy_room"), suggest("Bedroom"))
        assertEquals(listOf("study_test"), suggest("Biologietoetsen leren"))
    }

    @Test
    fun keywordPhrasesMatchAcrossExtraSpaces() {
        assertEquals(listOf("study_test"), suggest("Learn   for maths"))
    }

    @Test
    fun theTitleCountsMoreThanTheNotes() {
        // A title hit counts 2, a hit in the notes 1.
        assertEquals(listOf("tidy_room", "study_test"), suggest("Clean the desk", notes = "after the test"))
    }

    @Test
    fun tiesKeepTheCatalogOrder() {
        assertEquals(listOf("study_test", "tidy_room"), suggest("Tidy notes for the test"))
    }

    @Test
    fun nothingMatchesUnrelatedText() {
        assertEquals(emptyList<String>(), suggest("Call grandma", notes = "about Sunday"))
    }

    @Test
    fun blankOrWildcardOnlyKeywordsNeverMatch() {
        val odd = BreakdownTemplate("odd", "Odd", listOf("", "*", " ** "), listOf("Step"))
        assertEquals(emptyList<BreakdownTemplate>(), BreakdownEngine.suggest("Anything at all", "", listOf(odd)))
    }

    @Test
    fun stepsForSkipsStepsTheTaskAlreadyHas() {
        assertEquals(
            listOf("Gather your notes", "Review the first topic"),
            BreakdownEngine.stepsFor(studyTest, existingSteps = listOf(" find THE topics ")),
        )
    }

    @Test
    fun stepsForDropsBlankAndDuplicateTemplateSteps() {
        val messy = studyTest.copy(steps = listOf(" Read ", "", "read", "Write"))
        assertEquals(listOf("Read", "Write"), BreakdownEngine.stepsFor(messy, existingSteps = emptyList()))
    }
}
