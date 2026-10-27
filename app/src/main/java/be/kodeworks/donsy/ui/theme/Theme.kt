// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Pine40,
    onPrimary = Mist99,
    primaryContainer = Pine90,
    onPrimaryContainer = Pine10,
    secondary = Sage40,
    secondaryContainer = Sage90,
    tertiary = Dusk40,
    tertiaryContainer = Dusk90,
    background = Mist99,
    onBackground = Ink10,
    surface = Mist99,
    onSurface = Ink10,
    surfaceContainer = Mist95,
)

private val DarkColors = darkColorScheme(
    primary = Pine80,
    onPrimary = Pine10,
    primaryContainer = Pine40,
    onPrimaryContainer = Pine90,
    secondary = Sage80,
    secondaryContainer = Sage40,
    tertiary = Dusk80,
    tertiaryContainer = Dusk40,
    background = Ink6,
    onBackground = Ink90,
    surface = Ink6,
    onSurface = Ink90,
)

@Composable
fun DonsyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
