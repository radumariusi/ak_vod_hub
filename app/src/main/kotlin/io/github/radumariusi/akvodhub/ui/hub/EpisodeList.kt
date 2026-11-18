package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.db.EpisodeProgressEntity
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.data.tmdb.TmdbEpisode
import io.github.radumariusi.akvodhub.hub.DetailsState
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
    metadataFromSeason: TmdbEpisode? = null,
    seasonLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var individualMetadata by remember { mutableStateOf<TmdbEpisode?>(null) }
    LaunchedEffect(focused, seasonLoading, metadataFromSeason) {
        if (focused && !seasonLoading && metadataFromSeason == null && individualMetadata == null &&
            loadMetadata != null
        ) {
            delay(220)
            individualMetadata = loadMetadata(episode)
        }
    }
    val metadata = metadataFromSeason ?: individualMetadata
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
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
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

/** Ecranul dedicat sezoanelor. Lista rămâne limitată la episoadele disponibile din playlist. */
@Composable
internal fun SeriesEpisodeScreen(
    state: DetailsState,
    title: TitleEntity,
    onSelectSeason: (Int) -> Unit,
    loadMetadata: suspend (MergedEpisode) -> TmdbEpisode?,
    loadSeason: suspend (Int) -> Map<Int, TmdbEpisode>,
    onPlay: (MergedEpisode, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val seasons = state.episodes?.seasons.orEmpty()
    val selected = seasons.firstOrNull { it.number == state.selectedSeason } ?: seasons.firstOrNull()
    val listFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp)) {
        if (seasons.isEmpty() || selected == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Înapoi la fișă") }
                Message(
                    if (state.loadingEpisodes ||
                        state.episodes == null
                    ) {
                        "Se încarcă episoadele…"
                    } else {
                        "Nu sunt episoade disponibile."
                    },
                )
            }
            return@BoxWithConstraints
        }
        if (maxWidth < 570.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Înapoi la fișă") }
                EpisodeSeasonPicker(seasons, selected.number, onSelectSeason)
                SeasonEpisodes(
                    title,
                    selected,
                    state,
                    listFocus,
                    loadMetadata,
                    loadSeason,
                    onPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier
                        .weight(0.28f)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TitleHeading(title.name, state.logoPaths, height = 54.dp)
                    Text(
                        listOfNotNull(
                            title.year?.toString(),
                            "${seasons.size} sezoane disponibile",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onBack) { Text("Înapoi la fișă") }
                    LazyColumn(
                        state = rememberLazyListState(seasons.indexOf(selected).coerceAtLeast(0)),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(seasons, key = { it.number }) { season ->
                            val active = season.number == selected.number
                            val colors = MaterialTheme.colorScheme
                            val seasonColor = if (active) colors.secondary else colors.onSurface
                            Surface(
                                onClick = {
                                    if (active) listFocus.requestFocus() else onSelectSeason(season.number)
                                },
                                modifier =
                                    Modifier.fillMaxWidth().then(
                                        if (active) Modifier.focusRequester(seasonFocus) else Modifier,
                                    ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        seasonLabel(season.number),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = seasonColor,
                                    )
                                    Text(season.episodes.size.toString(), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                SeasonEpisodes(
                    title,
                    selected,
                    state,
                    listFocus,
                    loadMetadata,
                    loadSeason,
                    onPlay,
                    modifier =
                        Modifier
                            .weight(0.72f)
                            .focusProperties {
                                onExit = {
                                    if (requestedFocusDirection == FocusDirection.Left) seasonFocus.requestFocus()
                                }
                            }.focusGroup(),
                )
            }
        }
    }
}

@Composable
private fun SeasonEpisodes(
    title: TitleEntity,
    season: MergedSeason,
    state: DetailsState,
    listFocus: FocusRequester,
    loadMetadata: suspend (MergedEpisode) -> TmdbEpisode?,
    loadSeason: suspend (Int) -> Map<Int, TmdbEpisode>,
    onPlay: (MergedEpisode, Boolean) -> Unit,
    modifier: Modifier,
) {
    key(season.number) {
        val next = state.nextEpisode
        val initial = remember { (next?.let(season.episodes::indexOf) ?: -1).coerceAtLeast(0) }
        val entry = season.episodes.getOrElse(initial) { season.episodes.first() }
        val scroll = rememberLazyListState(initial)
        var metadata by remember { mutableStateOf<Map<Int, TmdbEpisode>>(emptyMap()) }
        var loading by remember { mutableStateOf(!title.isLocal) }
        LaunchedEffect(Unit) {
            if (!title.isLocal) metadata = loadSeason(season.number)
            loading = false
        }
        LaunchedEffect(Unit) {
            if (scroll.layoutInfo.visibleItemsInfo.none { it.index == initial }) scroll.scrollToItem(initial)
            withFrameNanos { }
            runCatching { listFocus.requestFocus() }
        }
        Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(seasonLabel(season.number), title.certification).joinToString(" · "),
                style = MaterialTheme.typography.headlineSmall,
            )
            LazyColumn(
                state = scroll,
                contentPadding = PaddingValues(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(season.episodes, key = {
                    it.number?.let { number -> "e:$number" }
                        ?: "source:${it.sources.first().id}"
                }) { episode ->
                    val progress = episode.number?.let { state.episodeProgress[episode.season to it] }
                    EpisodeRow(
                        episode = episode,
                        next = next == episode,
                        progress = progress,
                        seriesBackdrop = title.backdropPath,
                        loadMetadata = loadMetadata.takeIf { !title.isLocal && episode.number != null },
                        metadataFromSeason = episode.number?.let(metadata::get),
                        seasonLoading = loading,
                        modifier = if (episode == entry) Modifier.focusRequester(listFocus) else Modifier,
                        onClick = { onPlay(episode, progress?.finished == false) },
                    )
                }
            }
        }
    }
}
