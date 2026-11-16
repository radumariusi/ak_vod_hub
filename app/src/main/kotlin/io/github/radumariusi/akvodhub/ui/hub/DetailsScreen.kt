@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.catalog.Credits
import io.github.radumariusi.akvodhub.data.catalog.SourceDetails
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.hub.DetailsState
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.QualityGroup
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import kotlinx.coroutines.launch

/**
 * Pagina unui film sau serial: fundal, poster, descriere, notă, genuri, distribuție, apoi sursele grupate
 * după calitate și limbă (filme) sau sezoanele și episoadele unite din toate sursele (seriale).
 *
 * Redarea folosește deocamdată un player instalat pe TV (ex. VLC); playerul propriu vine în etapa 6.
 */
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    titleId: Long,
) {
    LaunchedEffect(titleId) { viewModel.open(titleId) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }

    fun play(entryId: Long) {
        scope.launch {
            val url = viewModel.streamUrl(entryId)
            message =
                if (url == null) {
                    "Nu pot construi linkul acestei surse (playlist-ul a fost șters?)."
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"))
                        null
                    } catch (e: ActivityNotFoundException) {
                        "Nu există un player video pe TV. Instalează VLC; playerul propriu vine în etapa următoare."
                    }
                }
        }
    }

    val details = state.details?.takeIf { state.titleId == titleId }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            details != null -> {
                DetailsContent(
                    state = state,
                    details = details,
                    onPlay = ::play,
                    onChooseSource = { title, sources, info ->
                        if (sources.size == 1) {
                            play(sources[0].id)
                        } else {
                            choice =
                                Choice(
                                    title,
                                    sources.mapIndexed { i, s ->
                                        (if (i == 0) "Recomandat: " else "") +
                                            sourceLabel(s, info[s.id])
                                    },
                                    0,
                                ) { i -> play(sources[i].id) }
                        }
                    },
                    onSelectSeason = viewModel::selectSeason,
                )
            }

            state.loading -> {
                Box(Modifier.padding(48.dp)) { Message("Se încarcă…") }
            }

            else -> {
                Box(Modifier.padding(48.dp)) { Message("Titlul nu mai există în playlist-uri.") }
            }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}

@Composable
private fun DetailsContent(
    state: DetailsState,
    details: TitleDetails,
    onPlay: (Long) -> Unit,
    onChooseSource: (String, List<SourceInfo>, Map<Long, SourceDetails>) -> Unit,
    onSelectSeason: (Int) -> Unit,
) {
    val title = details.title
    val background = MaterialTheme.colorScheme.background
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(title.id) { runCatching { playFocus.requestFocus() } }

    Images.backdrop(title.backdropPath)?.let {
        AsyncImage(
            model = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.45f,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to background,
                0.55f to background.copy(alpha = 0.75f),
                1f to Color.Transparent,
            ),
        ),
    )

    val episodes = state.episodes
    val season = episodes?.seasons?.firstOrNull { it.number == state.selectedSeason }
    val firstEpisode = season?.episodes?.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Header(details, state.credits) {
                val best = details.groups.firstOrNull()?.let { firstSource(it) }
                when {
                    best != null -> {
                        Button(onClick = { onPlay(best.id) }, modifier = Modifier.focusRequester(playFocus)) {
                            Text("Redă (${SourceGrouping.qualityLabel(best.quality)})")
                        }
                    }

                    firstEpisode != null -> {
                        Button(
                            onClick = {
                                onChooseSource(
                                    episodeLabel(firstEpisode),
                                    firstEpisode.sources,
                                    episodes?.sources.orEmpty(),
                                )
                            },
                            modifier = Modifier.focusRequester(playFocus),
                        ) { Text("Redă ${episodeCode(firstEpisode)}") }
                    }

                    else -> {
                        OutlinedButton(onClick = {}, modifier = Modifier.focusRequester(playFocus)) {
                            Text(if (state.loadingEpisodes) "Se încarcă episoadele…" else "Nicio sursă de redare")
                        }
                    }
                }
            }
        }

        if (title.mediaType == MediaType.MOVIE && details.groups.isNotEmpty()) {
            item("sources-title") {
                Text("Surse (${details.sources.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(details.groups, key = { "q:${it.quality}" }) { group ->
                QualityRow(group, details.sources, onPlay)
            }
        }

        if (episodes != null) {
            if (episodes.seasons.isNotEmpty()) {
                item("seasons") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(episodes.seasons, key = { it.number }) { s ->
                            val label = if (s.number == 0) "Altele" else "Sezonul ${s.number}"
                            if (s.number == state.selectedSeason) {
                                Button(onClick = { onSelectSeason(s.number) }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { onSelectSeason(s.number) }) { Text(label) }
                            }
                        }
                    }
                }
            } else if (!state.loadingEpisodes) {
                item("no-episodes") { Message("Nu am găsit episoade pentru acest serial.") }
            }
            season?.let { s ->
                items(s.episodes, key = { "e:${s.number}:${it.number}:${it.sources.first().id}" }) { episode ->
                    EpisodeRow(episode) { onChooseSource(episodeLabel(episode), episode.sources, episodes.sources) }
                }
            }
        }

        val cast = state.credits?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            item("cast") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Distribuție", style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(cast) { person ->
                            Surface(onClick = {}, modifier = Modifier.width(132.dp)) {
                                Column(
                                    Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    AsyncImage(
                                        model = Images.profile(person.profilePath),
                                        contentDescription = person.name,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                    Text(
                                        person.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    person.role?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    details: TitleDetails,
    credits: Credits?,
    actions: @Composable () -> Unit,
) {
    val title = details.title
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(
            Modifier
                .size(PosterWidth * 1.6f, PosterHeight * 1.6f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Images.poster(title.posterPath)?.let {
                AsyncImage(
                    model = it,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title.name,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (title.originalName != null && title.originalName != title.name) {
                Text(
                    title.originalName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val meta =
                listOfNotNull(
                    if (title.mediaType == MediaType.MOVIE) "Film" else "Serial",
                    title.year?.toString(),
                    title.rating?.takeIf { it > 0 }?.let {
                        "★ %.1f".format(it) +
                            (title.voteCount?.let { v -> " ($v voturi)" } ?: "")
                    },
                    details.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details.summary.isNotEmpty()) {
                Text(
                    details.summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            title.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            credits?.directors?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    "Regia: ${it.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (title.isLocal) {
                Text(
                    "Titlu negăsit (încă) pe TMDB: informațiile vin din playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

/** Un rând de calitate (ex. „4K”), cu câte un buton pentru fiecare sursă, grupate pe limbă. */
@Composable
private fun QualityRow(
    group: QualityGroup,
    sources: Map<Long, SourceDetails>,
    onPlay: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${SourceGrouping.qualityLabel(group.quality)} · ${group.count} " +
                if (group.count == 1) "sursă" else "surse",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            val all = group.languages.flatMap { it.sources }
            items(all, key = { it.id }) { source ->
                val info = sources[source.id]
                Surface(onClick = { onPlay(source.id) }, modifier = Modifier.widthIn(min = 150.dp, max = 260.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(sourceTags(source, info), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            info?.playlist.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: MergedEpisode,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(0.85f)) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                episode.number?.let {
                    "E%02d".format(it)
                } ?: "•",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
            )
            Column(Modifier.height(44.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    episode.title ?: episode.number?.let { "Episodul $it" } ?: "Episod",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    SourceGrouping.summary(SourceGrouping.group(episode.sources)) +
                        if (episode.sources.size > 1) " · ${episode.sources.size} surse" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun firstSource(group: QualityGroup): SourceInfo? =
    group.languages
        .firstOrNull()
        ?.sources
        ?.firstOrNull()

private fun episodeCode(episode: MergedEpisode) =
    if (episode.number != null) "S%02dE%02d".format(episode.season, episode.number) else "episodul"

private fun episodeLabel(episode: MergedEpisode) =
    listOfNotNull(
        episodeCode(episode).takeIf {
            episode.number != null
        },
        episode.title,
    ).joinToString(" · ").ifEmpty { "Episod" }

/** Ex. „EN · HDR10 · H265 · MKV”. */
private fun sourceTags(
    source: SourceInfo,
    info: SourceDetails?,
): String {
    val entry = info?.entry
    return listOfNotNull(
        source.languages.joinToString("+") { SourceGrouping.languageLabel(it) }.ifEmpty { "?" },
        source.hdr?.name?.replace('_', ' '),
        entry?.codec?.name,
        entry?.containerExt?.uppercase(),
        source.subtitles.takeIf { it.isNotEmpty() }?.let {
            "SUB " +
                it.joinToString("+") { s -> SourceGrouping.languageLabel(s) }
        },
    ).joinToString(" · ")
}

/** O sursă în lista de alegere, ex. „1080p · EN · H265 · MKV — Playlist A”. */
private fun sourceLabel(
    source: SourceInfo,
    info: SourceDetails?,
) = "${SourceGrouping.qualityLabel(source.quality)} · ${sourceTags(source, info)}" +
    (info?.playlist?.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: "")
