package io.github.radumariusi.akvodhub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.radumariusi.akvodhub.parser.catalog.AccentColor

private fun colors(accent: AccentColor) =
    darkColorScheme(
        primary = Color(accent.argb),
        onPrimary = Color(accent.onArgb),
        secondary = Color(0xFF8AB4F8),
        background = Color(0xFF0F1115),
        onBackground = Color(0xFFE8EAED),
        surface = Color(0xFF181B21),
        onSurface = Color(0xFFE8EAED),
        surfaceVariant = Color(0xFF242832),
        onSurfaceVariant = Color(0xFFB0B4BC),
    )

/** Temă întunecată, potrivită pentru TV. */
@Composable
fun AkVodHubTheme(accent: AccentColor = AccentColor.RED, content: @Composable () -> Unit) {
    val scheme = remember(accent) { colors(accent) }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
