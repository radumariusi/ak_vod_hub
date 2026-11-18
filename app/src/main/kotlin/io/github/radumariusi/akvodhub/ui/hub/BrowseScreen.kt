@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.Browser
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.catalog.HeroNeighbors
import io.github.radumariusi.akvodhub.parser.catalog.LibraryFilter
import io.github.radumariusi.akvodhub.parser.catalog.ProductionFilter
import io.github.radumariusi.akvodhub.parser.catalog.YearRange
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import io.github.radumariusi.akvodhub.ui.TvTextField
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
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
    showHero: Boolean = false,
    loadLogo: suspend (Long) -> List<String> = { emptyList() },
) {
    LaunchedEffect(browser) { browser.start() }
    val state by browser.state.collectAsState()
    val filter = state.filter
    var showFilters by remember { mutableStateOf(false) }
    var restoreFiltersFocus by remember { mutableStateOf(false) }
    val filtersFocus = remember { FocusRequester() }
    LaunchedEffect(showFilters) {
        if (!showFilters && restoreFiltersFocus) {
            filtersFocus.requestFocus()
            restoreFiltersFocus = false
        }
    }
    val grid = rememberLazyGridState()
    val preview = remember(filter) { mutableStateOf<TitleCard?>(null) }

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
            val visible = grid.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: -1) to (visible.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged()
            .collect { (first, last) -> browser.onVisible(first, last) }
    }
    LaunchedEffect(restoreKey, state.items) {
        val id = restoreKey?.takeIf { it.startsWith("browse:$screenKey:") }?.substringAfterLast(':')?.toLongOrNull()
        if (id != null) {
            val index = state.items.indexOfFirst { it.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                grid.scrollToItem(index)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridCells = GridCells.Adaptive(PosterWidth + 16.dp)
        val columns =
            with(LocalDensity.current) {
                val available = (maxWidth.roundToPx() - 2 * 6.dp.roundToPx()).coerceAtLeast(0)
                with(gridCells) { calculateCrossAxisCellSizes(available, 0).size }
            }
        val heroHeight = (maxHeight - PosterHeight - 102.dp).coerceIn(104.dp, 196.dp).coerceAtMost(maxHeight * 0.4f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fallback = state.items.firstOrNull()
            if (showHero && fallback != null) {
                CatalogHero(
                    fallback,
                    { preview.value },
                    loadLogo,
                    heroHeight,
                    neighborTitleIds = {
                        val index = preview.value?.let { card -> state.items.indexOfFirst { it.id == card.id } } ?: 0
                        HeroNeighbors.grid(index, columns, state.items.size).map { state.items[it].id }
                    },
                )
            }
            if (search) Text(title, style = MaterialTheme.typography.headlineMedium)
            if (search) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvTextField(
                        value = filter.text,
                        modifier = Modifier.weight(1f),
                        onValueChange = { browser.setFilter(filter.copy(text = it)) },
                        label = "Caută în playlist-uri",
                        placeholder = "Titlu, și în altă limbă (ex. „Der Pate”)",
                        imeAction = ImeAction.Search,
                    )
                    VoiceSearchButton { browser.setFilter(filter.copy(text = it)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!search) Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showFilters = true },
                    modifier = Modifier.focusRequester(filtersFocus),
                ) { Text("Filtre și sortare") }
                Text(filter.summary(), maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
            if (filter.type == MediaType.TV &&
                (filter.quality != null || filter.language != null || filter.originalAudioOnly)
            ) {
                Text(
                    "Potrivire pe o sursă declarată. Episoadele pot avea alte variante.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            when {
                state.items.isNotEmpty() -> {
                    LazyVerticalGrid(
                        state = grid,
                        columns = gridCells,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 32.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(state.items, key = { _, card -> card.id }) { _, card ->
                            val key = "browse:$screenKey:${card.id}"
                            PosterCard(
                                card = card,
                                onClick = { onOpen(card, key) },
                                onFocused = { if (showHero) preview.value = card },
                                requestFocus = key == restoreKey,
                                onFocusRequested = onRestored,
                                showTitle = search,
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
    }

    if (showFilters) {
        FilterPanel(filter, languages, search, browser) {
            restoreFiltersFocus = true
            showFilters = false
        }
    }
}

private fun CatalogFilter.summary(): String =
    listOfNotNull(
        sort.label,
        genre?.label,
        years?.label,
        quality?.let(SourceGrouping::qualityLabel),
        language?.let(::languageName),
        production.takeIf { it != ProductionFilter.ALL }?.label,
        library.takeIf { it != LibraryFilter.ALL }?.label,
        "Audio original".takeIf { originalAudioOnly },
    ).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    filter: CatalogFilter,
    languages: List<String>,
    search: Boolean,
    browser: Browser,
    dismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filter) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var naming by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(browser.presets()) }
    val applyFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = dismiss) {
        ThemedPanel(Modifier.width(560.dp)) {
            LaunchedEffect(Unit) { applyFocus.requestFocus() }
            Text("Filtre și sortare", style = MaterialTheme.typography.headlineSmall)
            Text(draft.summary(), style = MaterialTheme.typography.bodySmall)
            FilterBar(
                draft,
                languages,
                search,
                onChange = { draft = it },
                onChoose = { choice = it },
                modifier = Modifier.heightIn(max = 310.dp).weight(1f, fill = false),
            )
            if (!search && !naming) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(onClick = { naming = true }) { Text("Salvează combinația") }
                    if (presets.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            choice =
                                Choice("Combinații salvate", presets.map { it.name }, selected = null) {
                                    draft = presets[it].filter
                                }
                        }) { Text("Combinații") }
                        OutlinedButton(onClick = {
                            choice =
                                Choice("Șterge combinația", presets.map { it.name }, selected = null) {
                                    browser.removePreset(presets[it].name)
                                    presets = browser.presets()
                                }
                        }) { Text("Șterge") }
                    }
                }
            }
            if (naming) {
                TvTextField(presetName, { presetName = it.take(36) }, label = "Nume combinație")
                OutlinedButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        browser.savePreset(presetName, draft)
                        presets = browser.presets()
                        presetName = ""
                        naming = false
                    },
                ) { Text("Salvează") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(modifier = Modifier.focusRequester(applyFocus), onClick = {
                    browser.setFilter(draft)
                    dismiss()
                }) { Text("Aplică") }
                OutlinedButton(onClick = {
                    draft = CatalogFilter(type = filter.type.takeUnless { search }, text = filter.text)
                }) { Text("Resetează") }
                OutlinedButton(onClick = dismiss) { Text("Anulează") }
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
    modifier: Modifier,
) {
    val qualities = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD, VideoQuality.SD)
    val types = listOf(MediaType.MOVIE, MediaType.TV)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
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
        item("library") {
            FilterButton("Vizionare", filter.library.label, onClick = {
                val options = LibraryFilter.entries
                onChoose(
                    Choice("Vizionare", options.map { it.label }, filter.library.ordinal) {
                        onChange(filter.copy(library = options[it]))
                    },
                )
            })
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
