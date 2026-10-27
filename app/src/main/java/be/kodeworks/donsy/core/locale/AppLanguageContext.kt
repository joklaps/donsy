// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * The application context ignores the in-app language on Android 10–12, so texts made outside an
 * activity (notifications) use a context configured with it explicitly.
 */
fun Context.withAppLanguage(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val configuration = Configuration(resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locales.toLanguageTags())) }
    return createConfigurationContext(configuration)
}
