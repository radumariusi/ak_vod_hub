package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AppearanceTest {
    @Test
    fun oldOrUnknownBackupValuesKeepDefaultAppearance() {
        assertEquals(PosterSize.MEDIUM, PosterSize.decode(null))
        assertEquals(PosterSize.MEDIUM, PosterSize.decode("FUTURE_SIZE"))
        assertEquals(AccentColor.RED, AccentColor.decode(null))
        assertEquals(AccentColor.RED, AccentColor.decode("invalid"))
        for (size in PosterSize.entries) assertEquals(size, PosterSize.decode(size.name))
        for (color in AccentColor.entries) assertEquals(color, AccentColor.decode(color.name))
    }

    @Test
    fun presetsHaveReadableForegroundAndPortraitAspectRatio() {
        for (color in AccentColor.entries) {
            val a = luminance(color.argb)
            val b = luminance(color.onArgb)
            assertTrue(color.name, (max(a, b) + 0.05) / (min(a, b) + 0.05) >= 4.5)
        }
        assertTrue(PosterSize.SMALL.widthDp < PosterSize.MEDIUM.widthDp)
        assertTrue(PosterSize.MEDIUM.widthDp < PosterSize.LARGE.widthDp)
        for (size in PosterSize.entries) assertEquals(size.widthDp * 3, size.heightDp * 2)
    }

    private fun luminance(argb: Long): Double {
        fun component(shift: Int): Double {
            val value = ((argb shr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * component(16) + 0.7152 * component(8) + 0.0722 * component(0)
    }
}
