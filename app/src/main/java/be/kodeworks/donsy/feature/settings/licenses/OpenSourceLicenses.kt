// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.licenses

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import be.kodeworks.donsy.R

/** The licenses whose full text ships with the app, in `res/raw`. */
enum class OpenSourceLicense(@param:StringRes val title: Int, @param:RawRes val text: Int) {
    GPL_3(R.string.license_gpl_3_name, R.raw.license_gpl_3_0),
    APACHE_2(R.string.license_apache_2_name, R.raw.license_apache_2_0),
}

/** A library, or a family of libraries from one project, with its copyright holder. */
data class Library(val name: String, val holder: String)

/**
 * Everything in the release build (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`),
 * grouped by project. All of it is under the Apache License 2.0; a library under another license
 * also needs that license's text. Names and holders are proper names, so they aren't translated.
 */
val Libraries = listOf(
    Library("Android Jetpack (AndroidX): Compose, Room, DataStore, Navigation, Lifecycle, WorkManager and more", "The Android Open Source Project"),
    Library("Dagger and Hilt", "The Dagger Authors"),
    Library("Kotlin, kotlinx.coroutines, kotlinx.serialization and JetBrains annotations", "JetBrains s.r.o. and Kotlin contributors"),
    Library("Guava ListenableFuture", "The Guava Authors"),
    Library("Okio", "Square, Inc."),
    Library("javax.inject and Jakarta Dependency Injection", "The JSR-330 Expert Group"),
    Library("JSpecify", "The JSpecify Authors"),
    Library("JSR 305 annotations", "The FindBugs project"),
)

/** The license files are wrapped at 80 columns: join each paragraph's lines so it wraps to the screen. */
internal fun licenseParagraphs(text: String): List<String> =
    text.split(Regex("""\n\s*\n"""))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotBlank() }
