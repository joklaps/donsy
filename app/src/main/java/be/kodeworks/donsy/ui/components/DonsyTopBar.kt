// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.ui.theme.LocalTabAccent
import be.kodeworks.donsy.ui.theme.TabAccent
import be.kodeworks.donsy.ui.theme.tabAccent

/** Top bar for the four tabs: the tab's coloured icon, the screen title and the settings entry point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, onOpenSettings: () -> Unit) {
    val accent = LocalTabAccent.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { if (accent != null) TabAccentIcon(accent) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_open_settings))
            }
        },
    )
}

/** A small pastel circle with the tab's icon: a calm marker of where you are. Decorative only. */
@Composable
private fun TabAccentIcon(accent: TabAccent) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 4.dp)
            .size(36.dp)
            .background(MaterialTheme.colorScheme.tabAccent(accent.tint), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(accent.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Top bar for screens opened on top of a tab, with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_back),
                )
            }
        },
    )
}
