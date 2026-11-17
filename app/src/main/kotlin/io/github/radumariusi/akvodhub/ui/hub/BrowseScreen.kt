@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.Browser
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.catalog.ProductionFilter
import io.github.radumariusi.akvodhub.parser.catalog.YearRange
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import io.github.radumariusi.akvodhub.ui.TvTextField
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

/**
 * Grilă de titluri cu filtre: Filme, Seriale sau Căutare ([search] = true: câmp de text + filtru de tip).
 * Grila e paginată (pagina următoare se cere când derulezi spre capăt).
 */
@Composable
fun BrowseScreen(
    title: String,
    browser: Browser,
    languages: List<String>,
    search: Boolean,
    screenKey: String,
    onOpen: (TitleCard, String) -> Unit,
    restoreKey: String?,
    onRestored: () -> Unit,
) {
    LaunchedEffect(browser) { browser.start() }
    val state by browser.state.collectAsState()
    val filter = state.filter
    var choice by remember { mutableStateOf<Choice?>(null) }
    val grid = rememberLazyGridState()

    // Filtru nou → grila revine la început (dar nu la întoarcerea din detalii, cu același filtru).
    var shownFilter by rememberSaveable { mutableStateOf(filter.hashCode()) }
    LaunchedEffect(filter) {
        if (filter.hashCode() != shownFilter) {
            shownFilter = filter.hashCode()
            grid.scrollToItem(0)
        }
    }
    LaunchedEffect(grid, browser) {
        snapshotFlow {
            grid.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.distinctUntilChanged()
            .collect { browser.onVisible(it) }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    state.loading && state.items.isEmpty() -> "Se caută…"
                    state.items.isEmpty() -> ""
                    state.endReached -> "${state.items.size} titluri"
                    else -> "${state.items.size}+ titluri"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (search) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvTextField(
                    value = filter.text,
                    onValueChange = { browser.setFilter(filter.copy(text = it)) },
                    label = "Caută în playlist-uri",
                    placeholder = "Titlu, și în altă limbă (ex. „Der Pate”)",
                    imeAction = ImeAction.Search,
                )
                VoiceSearchButton { browser.setFilter(filter.copy(text = it)) }
            }
        }
        FilterBar(filter, languages, search, onChange = browser::setFilter, onChoose = { choice = it })

        when {
            state.items.isNotEmpty() -> {
                LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Adaptive(PosterWidth + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, card -> card.id }) { _, card ->
                        val key = "browse:$screenKey:${card.id}"
                        PosterCard(
                            card = card,
                            onClick = { onOpen(card, key) },
                            onFocused = {},
                            requestFocus = key == restoreKey,
                            onFocusRequested = onRestored,
                        )
                    }
                }
            }

            state.loading -> {
                Unit
            }

            search && filter.text.isBlank() && !filter.hasFilters -> {
                Message(
                    "Scrie un titlu sau alege un filtru. Caută după titlul TMDB, titlul original sau numele din playlist.",
                )
            }

            else -> {
                Message("Nu am găsit nimic cu aceste filtre.")
            }
        }
    }

    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}

@Composable
private fun FilterBar(
    filter: CatalogFilter,
    languages: List<String>,
    search: Boolean,
    onChange: (CatalogFilter) -> Unit,
    onChoose: (Choice) -> Unit,
) {
    val qualities = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD, VideoQuality.SD)
    val types = listOf(MediaType.MOVIE, MediaType.TV)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (search) {
            item("type") {
                FilterButton("Tip", filter.type?.let(::typeLabel), onClick = {
                    onChoose(
                        Choice("Tip", listOf("Toate") + types.map(::typeLabel), types.indexOf(filter.type) + 1) { i ->
                            onChange(filter.copy(type = types.getOrNull(i - 1)))
                        },
                    )
                })
            }
        }
        item("genre") {
            FilterButton("Gen", filter.genre?.label, onClick = {
                onChoose(
                    Choice(
                        "Gen",
                        listOf("Toate") + Genres.all.map { it.label },
                        Genres.all.indexOf(filter.genre) + 1,
                    ) { i ->
                        onChange(filter.copy(genre = Genres.all.getOrNull(i - 1)))
                    },
                )
            })
        }
        item("years") {
            FilterButton("An", filter.years?.label, onClick = {
                onChoose(
                    Choice(
                        "An",
                        listOf("Toți") + YearRange.all.map { it.label },
                        YearRange.all.indexOf(filter.years) + 1,
                    ) { i ->
                        onChange(filter.copy(years = YearRange.all.getOrNull(i - 1)))
                    },
                )
            })
        }
        item("quality") {
            FilterButton("Calitate", filter.quality?.let(SourceGrouping::qualityLabel), onClick = {
                onChoose(
                    Choice(
                        "Calitate",
                        listOf("Toate") + qualities.map(SourceGrouping::qualityLabel),
                        qualities.indexOf(filter.quality) + 1,
                    ) { i -> onChange(filter.copy(quality = qualities.getOrNull(i - 1))) },
                )
            })
        }
        item("language") {
            FilterButton("Limbă", filter.language?.let(::languageName), onClick = {
                onChoose(
                    Choice(
                        "Limbă audio",
                        listOf("Toate") + languages.map(::languageName),
                        languages.indexOf(filter.language) + 1,
                    ) { i -> onChange(filter.copy(language = languages.getOrNull(i - 1))) },
                )
            })
        }
        item("production") {
            FilterButton("Origine", filter.production.takeIf { it != ProductionFilter.ALL }?.label, onClick = {
                val options = ProductionFilter.entries
                onChoose(
                    Choice("Origine", options.map { it.label }, options.indexOf(filter.production)) { index ->
                        onChange(filter.copy(production = options[index]))
                    },
                )
            })
        }
        item("original") {
            FilterButton(
                "Doar în limba originală",
                if (filter.originalAudioOnly) "Da" else "Nu",
                active = filter.originalAudioOnly,
                onClick = { onChange(filter.copy(originalAudioOnly = !filter.originalAudioOnly)) },
            )
        }
        item("sort") {
            val sorts = CatalogSort.entries
            FilterButton("Ordine", filter.sort.label, active = false, onClick = {
                onChoose(
                    Choice("Ordine", sorts.map { it.label }, sorts.indexOf(filter.sort)) { i ->
                        onChange(filter.copy(sort = sorts[i]))
                    },
                )
            })
        }
        if (filter.hasFilters) {
            item("reset") {
                OutlinedButton(onClick = {
                    onChange(
                        filter.copy(
                            genre = null,
                            years = null,
                            quality = null,
                            language = null,
                            originalAudioOnly = false,
                            production = ProductionFilter.ALL,
                        ),
                    )
                }) { Text("Șterge filtrele") }
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    value: String?,
    onClick: () -> Unit,
    active: Boolean = value != null,
) {
    val text = "$label: ${value ?: "Toate"}"
    if (active) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

private fun typeLabel(type: MediaType) = if (type == MediaType.MOVIE) "Filme" else "Seriale"

private val romanian: Locale = Locale.forLanguageTag("ro")

/** Ex. „RO · română”, „MULTI · mai multe limbi”. */
fun languageName(code: String): String {
    val label = SourceGrouping.languageLabel(code)
    if (code == ParsedTitle.MULTI) return "$label · mai multe limbi"
    val name = Locale.forLanguageTag(code).getDisplayLanguage(romanian)
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) label else "$label · $name"
}
