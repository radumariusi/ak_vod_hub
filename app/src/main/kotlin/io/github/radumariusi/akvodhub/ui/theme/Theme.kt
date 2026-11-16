package io.github.radumariusi.akvodhub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors =
    darkColorScheme(
        primary = Color(0xFFE50914),
        onPrimary = Color.White,
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
fun AkVodHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
