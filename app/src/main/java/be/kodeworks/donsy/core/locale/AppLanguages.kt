// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.locale

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import be.kodeworks.donsy.R
import java.util.Locale

/**
 * Per-app language. The list of languages comes from the locale config that AGP generates
 * from the values-<lang> folders, so adding a translation needs no code change.
 */
object AppLanguages {

    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

    /** BCP 47 tags of the languages the app is translated into, in locale-config order. */
    fun supportedTags(context: Context): List<String> {
        val tags = mutableListOf<String>()
        context.resources.getXml(R.xml._generated_res_locale_config).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NAMESPACE, "name")?.let(tags::add)
                }
            }
        }
        return tags
    }

    /** The chosen app language, or null when the app follows the system language. */
    fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()

    /** Applies [tag], or follows the system language when it is null. Recreates visible activities. */
    fun apply(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    /** The language's own name for itself, e.g. "Nederlands", so people can find their language. */
    fun nativeDisplayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
