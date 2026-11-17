@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

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
import androidx.compose.runtime.remember
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.catalog.Credits
import io.github.radumariusi.akvodhub.data.catalog.Person
import io.github.radumariusi.akvodhub.data.catalog.SourceDetails
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.hub.DetailsState
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.parser.catalog.LocalizedOverview
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.QualityGroup
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.player.EpisodeOrder
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.player.PlayRequest
import io.github.radumariusi.akvodhub.player.PlayerActivity
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import io.github.radumariusi.akvodhub.ui.touch.Surface

/**
 * Pagina unui film sau serial: fundal, poster, descriere, notă, genuri, distribuție, apoi sursele grupate
 * după calitate și limbă (filme) sau sezoanele și episoadele unite din toate sursele (seriale).
 *
 * Redarea pornește playerul propriu; dacă sursa aleasă nu merge, trece singur la următoarea.
 */
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    titleId: Long,
    onActor: (Person) -> Unit,
    onOpen: (TitleCard) -> Unit,
) {
    LaunchedEffect(titleId) { viewModel.open(titleId) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun play(request: PlayRequest) {
        context.startActivity(PlayerActivity.intent(context, request))
    }

    val details = state.details?.takeIf { state.titleId == titleId }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            details != null -> {
                DetailsContent(
                    state = state,
                    details = details,
                    onPlay = ::play,
                    onSelectSeason = viewModel::selectSeason,
                    onActor = onActor,
                    onOpen = onOpen,
                )
            }

            state.loading -> {
                Box(Modifier.padding(48.dp)) { Message("Se încarcă…") }
            }

            else -> {
                Box(Modifier.padding(48.dp)) { Message("Titlul nu mai există în playlist-uri.") }
            }
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsState,
    details: TitleDetails,
    onPlay: (PlayRequest) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onActor: (Person) -> Unit,
    onOpen: (TitleCard) -> Unit,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Header(details, state.credits) {
                val first = episodes?.let { EpisodeOrder.linear(it.seasons).firstOrNull() }
                PlayActions(state, title.id, first, playFocus, onPlay)
            }
        }

        if (title.mediaType == MediaType.MOVIE && details.groups.isNotEmpty()) {
            item("sources-title") {
                Text("Surse (${details.sources.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(details.groups, key = { "q:${it.quality}" }) { group ->
                QualityRow(group, details.sources) { onPlay(PlayRequest(title.id, entryId = it)) }
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
                    EpisodeRow(episode) { onPlay(episodeRequest(title.id, episode)) }
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
                            Surface(onClick = {
                                if (person.id >
                                    0
                                ) {
                                    onActor(person)
                                }
                            }, modifier = Modifier.width(132.dp)) {
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

        // Descoperire: doar titluri care există în playlist-uri.
        val discovery = state.discovery
        discovery?.collection?.let { c ->
            item("collection") {
                DiscoverCards(
                    title = c.name.ifBlank { "Colecția" },
                    subtitle = c.summary,
                    cards = c.available,
                    onOpen = onOpen,
                )
            }
        }
        discovery?.rows.orEmpty().forEach { row ->
            item("discover:${row.key}") { DiscoverCards(row.title, null, row.items, onOpen) }
        }
    }
}

/** Un rând de postere pe pagina de detalii (colecție, recomandate, similare). */
@Composable
private fun DiscoverCards(
    title: String,
    subtitle: String?,
    cards: List<TitleCard>,
    onOpen: (TitleCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cards.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                items(cards, key = { it.id }) { card -> PosterCard(card, onClick = { onOpen(card) }, onFocused = {}) }
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
                    title.dubLabel,
                    title.runtimeMinutes?.let { "$it min" },
                    title.certification,
                    title.imdbRating?.let { rating ->
                        "IMDb %.1f".format(rating) + (title.imdbVotes?.let { " ($it voturi)" } ?: "")
                    },
                    title.rating?.takeIf { it > 0 }?.let {
                        "★ %.1f".format(it) +
                            (title.voteCount?.let { v -> " ($v voturi)" } ?: "")
                    },
                    details.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            title.tagline?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (details.summary.isNotEmpty()) {
                Text(
                    details.summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            LocalizedOverview.choose(title.overviewRo, title.overview)?.let {
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

/** Butoanele de redare din fișă: „Continuă de la 12:34” / „De la început” sau „Redă”. */
@Composable
private fun PlayActions(
    state: DetailsState,
    titleId: Long,
    firstEpisode: MergedEpisode?,
    playFocus: FocusRequester,
    onPlay: (PlayRequest) -> Unit,
) {
    val watch = state.watch
    val isSeries = state.details?.title?.mediaType == MediaType.TV
    if (!isSeries) {
        val best = state.recommended
        when {
            best == null -> {
                OutlinedButton(onClick = {}, modifier = Modifier.focusRequester(playFocus)) {
                    Text("Nicio sursă de redare")
                }
            }

            watch != null && Resume.shouldResume(watch.positionMs, watch.durationMs) -> {
                Button(
                    onClick = { onPlay(PlayRequest(titleId, resume = true)) },
                    modifier = Modifier.focusRequester(playFocus),
                ) { Text("Continuă de la ${Resume.format(watch.positionMs)}") }
                OutlinedButton(onClick = { onPlay(PlayRequest(titleId)) }) { Text("De la început") }
            }

            else -> {
                Button(onClick = { onPlay(PlayRequest(titleId)) }, modifier = Modifier.focusRequester(playFocus)) {
                    Text("Redă (${sourceShortLabel(best)})")
                }
            }
        }
        return
    }
    val episodes = state.episodes
    val current =
        watch?.let { w -> episodes?.let { EpisodeOrder.find(it.seasons, w.season, w.episode) } }
    when {
        current != null && watch != null -> {
            val resume = Resume.shouldResume(watch.positionMs, watch.durationMs)
            Button(
                onClick = { onPlay(episodeRequest(titleId, current, resume)) },
                modifier = Modifier.focusRequester(playFocus),
            ) {
                Text(
                    if (resume) {
                        "Continuă ${episodeCode(current)} (${Resume.format(watch.positionMs)})"
                    } else {
                        "Redă ${episodeCode(current)}"
                    },
                )
            }
            if (firstEpisode != null && firstEpisode != current) {
                OutlinedButton(onClick = { onPlay(episodeRequest(titleId, firstEpisode)) }) {
                    Text("De la ${episodeCode(firstEpisode)}")
                }
            }
        }

        firstEpisode != null -> {
            Button(
                onClick = { onPlay(episodeRequest(titleId, firstEpisode)) },
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

private fun episodeRequest(
    titleId: Long,
    episode: MergedEpisode,
    resume: Boolean = false,
) = PlayRequest(
    titleId = titleId,
    // Episoadele fără număr se găsesc după sursă.
    entryId = if (episode.number == null) episode.sources.firstOrNull()?.id else null,
    season = episode.season,
    episode = episode.number,
    resume = resume,
)

private fun episodeCode(episode: MergedEpisode) =
    if (episode.number != null) "S%02dE%02d".format(episode.season, episode.number) else "episodul"
