// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.ui.components.BackTopBar
import be.kodeworks.donsy.ui.components.ListSectionHeaderPadding
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.scrollIndicators

/** Donsy's own license, the libraries in the release build and their license (Settings → About Donsy). */
@Composable
fun LicensesScreen(onNavigateBack: () -> Unit, onOpenLicense: (OpenSourceLicense) -> Unit) {
    val listState = rememberLazyListState()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.licenses_title), onNavigateBack) }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
        ) {
            item { SectionHeader(stringResource(R.string.app_name), ListSectionHeaderPadding) }
            item { Paragraph(stringResource(R.string.licenses_donsy_body)) }
            item { ReadButton(stringResource(R.string.action_read_gpl)) { onOpenLicense(OpenSourceLicense.GPL_3) } }

            item { SectionHeader(stringResource(R.string.licenses_section_libraries), ListSectionHeaderPadding) }
            item { Paragraph(stringResource(R.string.licenses_libraries_body)) }
            items(Libraries) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(stringResource(R.string.license_copyright, library.holder)) },
                )
            }
            item { ReadButton(stringResource(R.string.action_read_apache)) { onOpenLicense(OpenSourceLicense.APACHE_2) } }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ReadButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(label) }
}

/** The full text of one license, in English: the only legally valid version. */
@Composable
fun LicenseTextScreen(license: OpenSourceLicense, onNavigateBack: () -> Unit) {
    val resources = LocalResources.current
    val paragraphs = remember(license) {
        licenseParagraphs(resources.openRawResource(license.text).bufferedReader().use { it.readText() })
    }
    val listState = rememberLazyListState()
    Scaffold(topBar = { BackTopBar(stringResource(license.title), onNavigateBack) }) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
        ) {
            items(paragraphs) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
