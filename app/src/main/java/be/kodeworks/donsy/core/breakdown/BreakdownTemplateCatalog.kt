// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.breakdown

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.breakdown.BreakdownTemplate

/**
 * The built-in templates. Their texts and keywords are per-locale resources (res/values/templates.xml),
 * so a translation brings its own keywords. Order matters: it breaks ties between equally good matches.
 */
object BreakdownTemplateCatalog {

    private class Entry(val id: String, @StringRes val name: Int, @ArrayRes val keywords: Int, @ArrayRes val steps: Int)

    private val entries = listOf(
        Entry("study_test", R.string.template_study_test_name, R.array.template_study_test_keywords, R.array.template_study_test_steps),
        Entry("write_text", R.string.template_write_text_name, R.array.template_write_text_keywords, R.array.template_write_text_steps),
        Entry("presentation", R.string.template_presentation_name, R.array.template_presentation_keywords, R.array.template_presentation_steps),
        Entry("homework", R.string.template_homework_name, R.array.template_homework_keywords, R.array.template_homework_steps),
        Entry("tidy_room", R.string.template_tidy_room_name, R.array.template_tidy_room_keywords, R.array.template_tidy_room_steps),
        Entry("call", R.string.template_call_name, R.array.template_call_keywords, R.array.template_call_steps),
        Entry("trip", R.string.template_trip_name, R.array.template_trip_keywords, R.array.template_trip_steps),
        Entry("read_book", R.string.template_read_book_name, R.array.template_read_book_keywords, R.array.template_read_book_steps),
    )

    fun load(resources: Resources): List<BreakdownTemplate> = entries.map { entry ->
        BreakdownTemplate(
            id = entry.id,
            name = resources.getString(entry.name),
            keywords = resources.getStringArray(entry.keywords).toList(),
            steps = resources.getStringArray(entry.steps).toList(),
        )
    }
}

/** The templates in the current UI language; reloaded when the language changes. */
@Composable
fun rememberBreakdownTemplates(): List<BreakdownTemplate> {
    val resources = LocalResources.current
    val locales = LocalConfiguration.current.locales
    return remember(resources, locales) { BreakdownTemplateCatalog.load(resources) }
}
