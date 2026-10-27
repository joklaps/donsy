// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import org.robolectric.RuntimeEnvironment

fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

fun quantityString(@PluralsRes id: Int, count: Int): String =
    RuntimeEnvironment.getApplication().resources.getQuantityString(id, count, count)

/** A bottom-navigation item; its label also appears as the screen title, so match the selectable node. */
fun navItem(@StringRes label: Int): SemanticsMatcher = hasText(string(label)) and isSelectable()
