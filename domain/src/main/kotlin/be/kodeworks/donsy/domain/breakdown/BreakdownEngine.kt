// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.breakdown

import java.text.Normalizer
import java.util.Locale

/**
 * A built-in way to break a task into steps. The app loads the texts from per-locale resources;
 * the domain only matches and adapts them.
 */
data class BreakdownTemplate(
    /** Stable code, never shown. */
    val id: String,
    val name: String,
    /**
     * Words that suggest this template. A keyword matches whole words; `*` stands for any letters,
     * so `quiz*` matches "quizzes" and `*toets` matches "biologietoets".
     */
    val keywords: List<String>,
    val steps: List<String>,
)

/** Offline, rule-based task breakdown (docs/spec.md, "Breakdown templates"). */
object BreakdownEngine {

    /** Keyword hits in the title count double: the title says what the task is about. */
    private const val TITLE_WEIGHT = 2
    private const val NOTES_WEIGHT = 1

    /**
     * Templates whose keywords occur in the task, best match first. Ties keep the order of
     * [templates]. Empty when nothing matches.
     */
    fun suggest(title: String, notes: String, templates: List<BreakdownTemplate>): List<BreakdownTemplate> {
        val normalizedTitle = normalize(title)
        val normalizedNotes = normalize(notes)
        return templates
            .map { template ->
                val score = template.keywords.sumOf { keyword ->
                    val pattern = keywordPattern(keyword) ?: return@sumOf 0
                    when {
                        pattern.containsMatchIn(normalizedTitle) -> TITLE_WEIGHT
                        pattern.containsMatchIn(normalizedNotes) -> NOTES_WEIGHT
                        else -> 0
                    }
                }
                template to score
            }
            .filter { (_, score) -> score > 0 }
            // sortedByDescending is stable, so equal scores keep the catalog order.
            .sortedByDescending { (_, score) -> score }
            .map { (template, _) -> template }
    }

    /**
     * The template's steps adapted to the task: steps it already has (ignoring case and accents)
     * are left out, and so are duplicates within the template.
     */
    fun stepsFor(template: BreakdownTemplate, existingSteps: List<String>): List<String> {
        val seen = existingSteps.mapTo(mutableSetOf()) { normalize(it) }
        return template.steps
            .map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(normalize(it)) }
    }

    /** Lower case, accents removed, runs of whitespace collapsed. */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    private fun keywordPattern(keyword: String): Regex? {
        val parts = normalize(keyword).split('*')
        if (parts.all { it.isEmpty() }) return null
        val body = parts.joinToString(WORD_CHARS) { if (it.isEmpty()) "" else Regex.escape(it) }
        return Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])")
    }

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
    private const val WORD_CHARS = "[\\p{L}\\p{N}]*"
}
