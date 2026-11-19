@file:OptIn(ExperimentalFoundationApi::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Derulare verticală „cu pivot”, ca pe TV: rândul cu focus ajunge mereu la aceeași înălțime ([pivot] de la
 * marginea de sus), deci se vede întreg și nu „sare” diferit de la un poster la altul.
 * Rândurile orizontale din interior păstrează comportamentul obișnuit (vezi [DefaultScroll]).
 */
@Composable
fun PivotScroll(
    pivot: Dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val pivotPx = with(LocalDensity.current) { pivot.toPx() }
    val default = LocalBringIntoViewSpec.current
    val spec =
        remember(pivotPx) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                    if (size + pivotPx > containerSize) {
                        default.calculateScrollDistance(offset, size, containerSize)
                    } else {
                        offset - pivotPx
                    }
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, LocalDefaultScroll provides default) { content() }
}

private val LocalDefaultScroll = androidx.compose.runtime.staticCompositionLocalOf<BringIntoViewSpec?> { null }

/** Readuce derularea obișnuită pentru un rând orizontal aflat într-un [PivotScroll]. */
@Composable
fun DefaultScroll(content: @Composable () -> Unit) {
    val default = LocalDefaultScroll.current
    if (default == null) {
        content()
    } else {
        CompositionLocalProvider(LocalBringIntoViewSpec provides default) { content() }
    }
}
