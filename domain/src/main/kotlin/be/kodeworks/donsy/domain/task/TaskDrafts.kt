// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.task

import be.kodeworks.donsy.domain.model.TaskDraft

object TaskDrafts {

    /**
     * Turns a captured thought into a task draft: the first non-blank line becomes the title,
     * everything after it the notes.
     */
    fun fromBrainDumpText(text: String): TaskDraft {
        val lines = text.trim().lines()
        val title = lines.first().trim()
        val notes = lines.drop(1).joinToString("\n").trim()
        return TaskDraft(title = title, notes = notes)
    }
}
