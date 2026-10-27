// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.task

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDraftsTest {

    @Test
    fun singleLineBecomesTheTitle() {
        val draft = TaskDrafts.fromBrainDumpText("  Prepare for my biology test ")
        assertEquals("Prepare for my biology test", draft.title)
        assertEquals("", draft.notes)
    }

    @Test
    fun firstLineIsTitleAndTheRestNotes() {
        val draft = TaskDrafts.fromBrainDumpText("Plan Sam's party\n\nCake?\nInvite the team \n")
        assertEquals("Plan Sam's party", draft.title)
        assertEquals("Cake?\nInvite the team", draft.notes)
    }

    @Test
    fun leadingBlankLinesAreSkipped() {
        assertEquals("Call grandma", TaskDrafts.fromBrainDumpText("\n\n Call grandma\n").title)
    }
}
