package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.db.TitleCard
import kotlinx.coroutines.delay

@Composable
internal fun TitleBackdrop(path: String?, modifier: Modifier = Modifier, opacity: Float = 0.82f) {
    val artwork = Images.hero(path) ?: return
    val background = MaterialTheme.colorScheme.background
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = opacity,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to background,
                    0.40f to background.copy(alpha = 0.78f),
                    0.72f to background.copy(alpha = 0.15f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to background),
            ),
        )
    }
}

/** Cadru fix. Imaginea și sigla se schimbă numai când navigarea se oprește pe un titlu. */
@Composable
internal fun CatalogHero(
    fallback: TitleCard,
    focused: () -> TitleCard?,
    logo: suspend (Long) -> List<String>,
    height: Dp,
    onBackdropChanged: ((String?) -> Unit)? = null,
) {
    val target = focused() ?: fallback
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target.id != shown.id) delay(300)
        shown = target
    }
    var logoPaths by remember(shown.id) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(shown.id) { logoPaths = logo(shown.id) }
    LaunchedEffect(shown.backdropPath) { onBackdropChanged?.invoke(shown.backdropPath) }
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        if (onBackdropChanged == null) TitleBackdrop(shown.backdropPath, Modifier.matchParentSize())
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.7f)
                .widthIn(max = 550.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            TitleHeading(shown.name, logoPaths, height = if (height < 132.dp) 52.dp else 68.dp)
            Text(
                shown.meta(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            shown.overview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = ((height.value.toInt() - 88) / 22).coerceIn(1, 4),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
