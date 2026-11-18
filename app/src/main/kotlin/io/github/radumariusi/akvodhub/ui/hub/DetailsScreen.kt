@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.catalog.Person
import io.github.radumariusi.akvodhub.data.catalog.SourceDetails
import io.github.radumariusi.akvodhub.data.catalog.SourceTracks
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.hub.DetailsState
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.QualityGroup
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.player.EpisodeOrder
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.player.PlayRequest
import io.github.radumariusi.akvodhub.player.PlayerActivity
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import io.github.radumariusi.akvodhub.ui.touch.Surface
import kotlinx.coroutines.launch

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
    val saved = rememberSaveableStateHolder()
    var showingEpisodes by rememberSaveable(titleId) { mutableStateOf(false) }
    var restoreEpisodeAction by rememberSaveable(titleId) { mutableStateOf(false) }
    BackHandler(enabled = showingEpisodes) {
        showingEpisodes = false
        restoreEpisodeAction = true
    }

    fun play(request: PlayRequest) {
        context.startActivity(PlayerActivity.intent(context, request))
    }

    val details = state.details?.takeIf { state.titleId == titleId }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            details != null && showingEpisodes -> {
                saved.SaveableStateProvider("episodes:$titleId") {
                    SeriesEpisodeScreen(
                        state = state,
                        title = details.title,
                        onSelectSeason = viewModel::selectSeason,
                        loadMetadata = viewModel::episodeMetadata,
                        loadSeason = viewModel::seasonMetadata,
                        onPlay = { episode, resume -> play(episodeRequest(titleId, episode, resume)) },
                        onBack = {
                            showingEpisodes = false
                            restoreEpisodeAction = true
                        },
                    )
                }
            }

            details != null -> {
                saved.SaveableStateProvider("detail:$titleId") {
                    DetailsContent(
                        state = state,
                        details = details,
                        onPlay = ::play,
                        onWatchlist = viewModel::toggleWatchlist,
                        loadSourceTracks = viewModel::sourceTracks,
                        onWatched = viewModel::toggleWatched,
                        onShowEpisodes = { showingEpisodes = true },
                        restoreEpisodeAction = restoreEpisodeAction,
                        onEpisodeActionRestored = { restoreEpisodeAction = false },
                        onActor = onActor,
                        sourceReason =
                            state.recommended?.let {
                                viewModel.sourceReason(
                                    it,
                                    details.title.originalLanguage,
                                )
                            },
                        onOpen = onOpen,
                    )
                }
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
    onWatchlist: () -> Unit,
    loadSourceTracks: suspend (Long) -> SourceTracks?,
    onWatched: () -> Unit,
    onShowEpisodes: () -> Unit,
    restoreEpisodeAction: Boolean,
    onEpisodeActionRestored: () -> Unit,
    onActor: (Person) -> Unit,
    onOpen: (TitleCard) -> Unit,
    sourceReason: String?,
) {
    val title = details.title
    val playFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tracksOpen by remember(title.id) { mutableStateOf(false) }
    val inspectedEpisode =
        state.nextEpisode ?: state.episodes
            ?.seasons
            ?.firstOrNull()
            ?.episodes
            ?.firstOrNull()
    val trackSources =
        remember(details.groups, inspectedEpisode) {
            inspectedEpisode?.sources
                ?: details.groups.flatMap { group -> group.languages.flatMap { it.sources } }.distinctBy { it.id }
        }
    if (tracksOpen && trackSources.isNotEmpty()) {
        SourceTracksDialog(
            trackSources,
            if (inspectedEpisode != null) state.episodes?.sources.orEmpty() else details.sources,
            state.episodes
                ?.seasons
                .orEmpty()
                .flatMap { it.episodes },
            inspectedEpisode,
            loadSourceTracks,
            onClose = { tracksOpen = false },
        )
    }
    val hasSources = title.mediaType == MediaType.MOVIE && details.groups.isNotEmpty()
    LaunchedEffect(title.id) {
        if (restoreEpisodeAction) {
            list.scrollToItem(0)
            runCatching { episodesFocus.requestFocus() }
            onEpisodeActionRestored()
        } else {
            runCatching { playFocus.requestFocus() }
        }
    }

    TitleBackdrop(title.backdropPath, Modifier.fillMaxSize(), opacity = 0.94f)

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Box(Modifier.padding(horizontal = 40.dp)) {
            TitleHeading(title.name, state.logoPaths, height = 70.dp)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("header") {
                Header(details) {
                    val first =
                        state.nextEpisode ?: state.episodes
                            ?.seasons
                            ?.firstOrNull()
                            ?.episodes
                            ?.firstOrNull()
                    PlayActions(state, title.id, first, playFocus, onPlay)
                    if (title.mediaType == MediaType.TV) {
                        OutlinedButton(
                            onClick = onShowEpisodes,
                            modifier = Modifier.fillMaxWidth().focusRequester(episodesFocus),
                        ) {
                            Text("Sezoane și episoade", modifier = Modifier.fillMaxWidth())
                        }
                    } else if (hasSources) {
                        OutlinedButton(
                            onClick = { scope.launch { list.animateScrollToItem(1) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Alege sursa", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedButton(
                        onClick = { tracksOpen = true },
                        enabled = trackSources.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Audio și subtitrări", modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(
                        onClick = onWatchlist,
                        enabled = !state.changingLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.inWatchlist) "Scoate din Lista mea" else "Adaugă în Lista mea",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (title.mediaType == MediaType.MOVIE && details.groups.isNotEmpty()) {
                item("sources-title") {
                    Text("Surse (${details.sources.size})", style = MaterialTheme.typography.titleMedium)
                    sourceReason?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (details.groups.any { it.quality == null }) {
                        Text(
                            "Calitatea surselor nedeclarate se completează după detectarea rezoluției la redare.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(details.groups, key = { "q:${it.quality}" }) { group ->
                    QualityRow(group, details.sources) { onPlay(PlayRequest(title.id, entryId = it)) }
                }
            }

            item("title-info") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.credits?.directors?.takeIf { it.isNotEmpty() }?.let {
                        Text("Regia: ${it.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onWatched, enabled = !state.changingLibrary) {
                        Text(if (state.watched) "Marchează nevăzut" else "Marchează văzut")
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
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            items(cast) { person ->
                                Surface(onClick = {
                                    if (person.id >
                                        0
                                    ) {
                                        onActor(person)
                                    }
                                }, modifier = Modifier.width(120.dp)) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        AsyncImage(
                                            model = Images.profile(person.profilePath),
                                            contentDescription = person.name,
                                            contentScale = ContentScale.Fit,
                                            modifier =
                                                Modifier
                                                    .size(96.dp, 144.dp)
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        Text(
                                            person.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            person.role.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LocalContentColor.current.copy(alpha = 0.9f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                items(cards, key = { it.id }) { card ->
                    PosterCard(card, onClick = { onOpen(card) }, onFocused = {}, showTitle = false)
                }
            }
        }
    }
}

@Composable
private fun Header(
    details: TitleDetails,
    actions: @Composable () -> Unit,
) {
    val title = details.title
    Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var expanded by rememberSaveable(title.id) { mutableStateOf(false) }
        var summaryTruncated by remember(title.id) { mutableStateOf(false) }
        var metaTruncated by remember(title.id) { mutableStateOf(false) }
        var overviewTruncated by remember(title.id) { mutableStateOf(false) }
        val meta =
            listOfNotNull(
                if (title.mediaType == MediaType.MOVIE) "Film" else "Serial",
                title.year?.toString(),
                title.dubLabel,
                title.runtimeMinutes?.let { "$it min" },
                title.certification,
                title.rating?.takeIf { it > 0 }?.let { "TMDB %.1f".format(it) } ?: "TMDB —",
                title.imdbRating?.let { "IMDb %.1f".format(it) } ?: "IMDb —",
                details.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · ")
        Text(
            meta,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) metaTruncated = it.hasVisualOverflow },
        )
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
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) summaryTruncated = it.hasVisualOverflow },
            )
        }
        title.overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overviewTruncated = it.hasVisualOverflow },
            )
        }
        if (expanded || metaTruncated || overviewTruncated || summaryTruncated) {
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Arată mai puțin" else "Arată mai mult")
            }
        }
        Column(Modifier.widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
        if (title.isLocal) {
            Text(
                "Titlu negăsit (încă) pe TMDB: informațiile vin din playlist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Câte un buton pe limbă/calitate; variantele se aleg numai când deschizi grupul. */
@Composable
private fun QualityRow(
    group: QualityGroup,
    sources: Map<Long, SourceDetails>,
    onPlay: (Long) -> Unit,
) {
    var choice by remember(group) { mutableStateOf<Choice?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${SourceGrouping.qualityLabel(group.quality)} · ${group.count} " +
                if (group.count == 1) "sursă" else "surse",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            items(group.languages, key = { it.language ?: "unknown" }) { language ->
                OutlinedButton(onClick = {
                    val variants = language.sources
                    choice =
                        Choice(
                            "${SourceGrouping.qualityLabel(
                                group.quality,
                            )} · ${SourceGrouping.languageLabel(language.language)}",
                            variants.mapIndexed { index, source ->
                                val info = sources[source.id]
                                "${sourceTags(source, info)} · ${info?.playlist.orEmpty()} · varianta ${index + 1}"
                            },
                            selected = null,
                            onPick = { index -> onPlay(variants[index].id) },
                        )
                }) {
                    Text(
                        "${SourceGrouping.languageLabel(language.language)} · ${language.sources.size} " +
                            if (language.sources.size == 1) "sursă" else "surse",
                    )
                }
            }
        }
    }
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
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
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().focusRequester(playFocus)) {
                    Text("Nicio sursă de redare")
                }
            }

            watch != null && Resume.shouldResume(watch.positionMs, watch.durationMs) -> {
                Button(
                    onClick = { onPlay(PlayRequest(titleId, resume = true)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
                ) { Text("Continuă de la ${Resume.format(watch.positionMs)}") }
                OutlinedButton(onClick = { onPlay(PlayRequest(titleId)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("De la început", modifier = Modifier.fillMaxWidth())
                }
            }

            else -> {
                Button(onClick = {
                    onPlay(PlayRequest(titleId))
                }, modifier = Modifier.fillMaxWidth().focusRequester(playFocus)) {
                    Text("Redă")
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
                modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
            ) {
                Text(
                    if (resume) {
                        "Continuă ${episodeCode(current)} (${Resume.format(watch.positionMs)})"
                    } else {
                        "Redă ${episodeCode(current)}"
                    },
                )
            }
            if (resume) {
                OutlinedButton(
                    onClick = { onPlay(episodeRequest(titleId, current)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("De la începutul episodului", modifier = Modifier.fillMaxWidth())
                }
            }
        }

        firstEpisode != null -> {
            Button(
                onClick = { onPlay(episodeRequest(titleId, firstEpisode)) },
                modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
            ) { Text("Redă ${episodeCode(firstEpisode)}") }
        }

        else -> {
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().focusRequester(playFocus)) {
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

@Composable
private fun SourceTracksDialog(
    sources: List<SourceInfo>,
    details: Map<Long, SourceDetails>,
    episodes: List<MergedEpisode>,
    initialEpisode: MergedEpisode?,
    load: suspend (Long) -> SourceTracks?,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf<SourceInfo?>(null) }
    var episode by remember { mutableStateOf(initialEpisode) }
    var pickingEpisode by remember { mutableStateOf(false) }
    val source = selected
    if (pickingEpisode) {
        ChoiceDialog(
            Choice(
                "Alege episodul",
                episodes.map { listOfNotNull(episodeCode(it), it.title).joinToString(" · ") },
                episodes.indexOfFirst { it == episode }.coerceAtLeast(0),
            ) { episode = episodes[it] },
            onDismiss = { pickingEpisode = false },
        )
        return
    }
    if (source == null) {
        val visibleSources = episode?.sources ?: sources
        val canPickEpisode = episodes.size > 1
        val labels =
            visibleSources.mapIndexed { index, item ->
                "${index + 1}. ${sourceTags(item, details[item.id])} · ${details[item.id]?.playlist.orEmpty()}"
            }
        ChoiceDialog(
            Choice(
                listOfNotNull("Audio și subtitrări", episode?.let(::episodeCode)).joinToString(" · "),
                if (canPickEpisode) listOf("Alege episodul") + labels else labels,
                selected = if (canPickEpisode) 1 else 0,
            ) {
                if (canPickEpisode && it == 0) {
                    pickingEpisode = true
                } else {
                    selected = visibleSources[it - if (canPickEpisode) 1 else 0]
                }
            },
            onDismiss = { if (selected == null && !pickingEpisode) onClose() },
        )
    } else {
        var cached by remember(source.id) { mutableStateOf<SourceTracks?>(null) }
        LaunchedEffect(source.id) { cached = load(source.id) }
        val audio =
            source.languages
                .map(SourceGrouping::languageLabel)
                .joinToString(", ")
                .ifEmpty { "Nedeclarat" }
        val subtitles =
            source.subtitles
                .map(SourceGrouping::languageLabel)
                .joinToString(", ")
                .ifEmpty { "Nedeclarate" }
        val confirmed = cached
        val title = sourceTags(source, details[source.id])
        val back = remember { FocusRequester() }
        Dialog(onDismissRequest = { selected = null }) {
            ThemedPanel(Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                LaunchedEffect(Unit) { back.requestFocus() }
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { selected = null }, modifier = Modifier.focusRequester(back)) {
                    Text("Înapoi la surse")
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp).weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { TrackDetailLine("Audio declarat în playlist · $audio") }
                    item { TrackDetailLine("Subtitrări declarate în playlist · $subtitles") }
                    if (confirmed == null) {
                        item { TrackDetailLine("Pistele acestei variante se confirmă în timpul redării.") }
                    } else {
                        item { Text("Audio detectat", style = MaterialTheme.typography.titleSmall) }
                        items(confirmed.audio.ifEmpty { listOf("Nicio pistă detectată") }) { TrackDetailLine(it) }
                        item { Text("Subtitrări interne detectate", style = MaterialTheme.typography.titleSmall) }
                        items(confirmed.subtitles.ifEmpty { listOf("Nicio pistă detectată") }) { TrackDetailLine(it) }
                    }
                    item { TrackDetailLine("Subtitrările externe se caută în player.") }
                }
            }
        }
    }
}

@Composable
private fun TrackDetailLine(label: String) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .background(if (focused) colors.surfaceVariant else colors.surface, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
