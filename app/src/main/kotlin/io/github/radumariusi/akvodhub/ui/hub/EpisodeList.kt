package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.db.EpisodeProgressEntity
import io.github.radumariusi.akvodhub.data.tmdb.TmdbEpisode
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeMerge
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.catalog.MergedSeason
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import io.github.radumariusi.akvodhub.ui.touch.Surface
import kotlinx.coroutines.delay

@Composable
internal fun EpisodeSeasonPicker(seasons: List<MergedSeason>, selected: Int?, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Episoade", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { open = true }) {
            Text(selected?.let(::seasonLabel) ?: "Alege sezonul")
        }
    }
    if (open) {
        ChoiceDialog(
            Choice(
                "Alege sezonul",
                seasons.map { seasonLabel(it.number) },
                seasons.indexOfFirst { it.number == selected }.coerceAtLeast(0),
            ) { onSelect(seasons[it].number) },
            onDismiss = { open = false },
        )
    }
}

private fun seasonLabel(number: Int): String = if (number == 0) "Episoade speciale" else "Sezonul $number"

@Composable
internal fun EpisodeRow(
    episode: MergedEpisode,
    next: Boolean,
    progress: EpisodeProgressEntity?,
    seriesBackdrop: String?,
    loadMetadata: (suspend (MergedEpisode) -> TmdbEpisode?)?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<TmdbEpisode?>(null) }
    LaunchedEffect(focused) {
        if (focused && metadata == null && loadMetadata != null) {
            delay(220)
            metadata = loadMetadata(episode)
        }
    }
    val name = EpisodeMerge.namedEpisode(metadata?.name) ?: episode.title
    val label =
        when {
            episode.number != null && name != null -> "${episode.number}. $name"
            episode.number != null -> "Episodul ${episode.number}"
            else -> name ?: "Episod"
        }
    val duration =
        progress?.durationMs?.takeIf { it > 0 }?.let(Resume::format)
            ?: metadata?.runtime?.takeIf { it > 0 }?.let { "$it min" }
    val status =
        when {
            progress?.finished == true -> "Văzut"
            progress != null -> "Început · ${Resume.format(progress.positionMs)}"
            else -> null
        }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                val art = Images.thumbnail(metadata?.stillPath ?: seriesBackdrop)
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (focused || art == null) {
                    Text(
                        "▶",
                        color = colors.onSurface,
                        modifier =
                            Modifier
                                .background(colors.surface.copy(alpha = 0.88f), MaterialTheme.shapes.small)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                val fraction =
                    when {
                        progress == null -> {
                            0f
                        }

                        progress.finished -> {
                            1f
                        }

                        progress.durationMs > 0 -> {
                            (progress.positionMs.toDouble() / progress.durationMs).toFloat().coerceIn(0f, 1f)
                        }

                        else -> {
                            0f
                        }
                    }
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(colors.surface),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(colors.secondary))
                    }
                }
            }
            Column(
                Modifier.weight(1f).heightIn(min = 70.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    duration?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                }
                val summary = metadata?.overview?.takeIf(String::isNotBlank)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val info = listOfNotNull("Următorul disponibil".takeIf { next }, status).joinToString(" · ")
                if (info.isNotEmpty()) {
                    Text(
                        info,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
