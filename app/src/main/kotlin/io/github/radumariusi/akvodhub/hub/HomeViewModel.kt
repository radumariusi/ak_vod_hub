package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.ContinueItem
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.discover.DiscoverRepository
import io.github.radumariusi.akvodhub.data.discover.DiscoverRow
import io.github.radumariusi.akvodhub.parser.catalog.CatalogCursor
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.catalog.HomeRows
import io.github.radumariusi.akvodhub.parser.catalog.KeysetPage
import io.github.radumariusi.akvodhub.parser.catalog.NewsCursor
import io.github.radumariusi.akvodhub.parser.catalog.PageWindow
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Un rând de pe Acasă. [filter] null = rând fără paginare („Continuă vizionarea”, recomandări, cataloage din
 * addon-uri).
 */
data class HomeRow(
    val key: String,
    val title: String,
    val items: List<TitleCard>,
    val filter: CatalogFilter?,
    val endReached: Boolean,
    val hasEarlier: Boolean = false,
    val continueInfo: Map<Long, ContinueItem> = emptyMap(),
)

data class HomeState(
    val loading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val catalogLabels: Map<String, String> = emptyMap(),
)

/**
 * Rândurile de pe Acasă. Fiecare rând încarcă [PAGE] titluri și cere următoarea pagină când focusul se
 * apropie de capăt, deci nici la 100.000 de titluri nu ținem în memorie mai mult decât s-a văzut.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: HubRepository,
        private val discover: DiscoverRepository,
    ) : ViewModel() {
        val prefs = repository.sourcePreferences

        private val mutableState = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = mutableState.asStateFlow()

        private sealed interface HomeCursor {
            data class Catalog(val cursor: CatalogCursor) : HomeCursor

            data class News(val cursor: NewsCursor) : HomeCursor

            data class Watchlist(val id: Long) : HomeCursor
        }

        private val windows = mutableMapOf<String, PageWindow<HomeCursor, TitleCard>>()
        private val loadingMore = mutableMapOf<String, Job>()

        /** Rândurile din catalog (paginate) și „Continuă vizionarea”. */
        private var base: List<HomeRow> = emptyList()

        /** Recomandări personale (din istoric) și cataloagele addon-urilor; se încarcă separat, fără să țină Acasă. */
        private var personal: List<HomeRow> = emptyList()
        private var catalogs: List<HomeRow> = emptyList()
        private var loaded = false
        private var reloadJob: Job? = null
        private var reloadPending = false
        private var preparationStarted = false
        private var personalJob: Job? = null
        private var catalogJob: Job? = null

        private val specs: List<Triple<String, String, CatalogFilter>> =
            listOf(
                Triple("movies", "Filme populare", CatalogFilter(type = MediaType.MOVIE)),
                Triple("series", "Seriale populare", CatalogFilter(type = MediaType.TV)),
                Triple("rated", "Cele mai apreciate", CatalogFilter(sort = CatalogSort.RATING)),
            ) + Genres.home.map { Triple("genre:${it.key}", it.label, CatalogFilter(genre = it)) }

        init {
            // După fiecare reindexare (import, potrivire TMDB, playlist șters), rândurile se reîncarcă.
            viewModelScope.launch {
                repository.catalogVersion.drop(1).collect { reload() }
            }
            viewModelScope.launch {
                repository.watchVersion.drop(1).collect {
                    if (reloadJob?.isActive == true) {
                        reloadPending = true
                    } else {
                        refreshContinueWatching()
                        refreshEpisodeNews()
                        refreshWatchlist()
                        loadPersonal(delayMs = PERSONAL_DEBOUNCE_MS)
                    }
                }
            }
            viewModelScope.launch {
                discover.installedAddons.drop(1).collect { loadCatalogs() }
            }
            viewModelScope.launch {
                prefs
                    .map { it.homeOrder to it.homeHidden }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { reload() }
            }
            reload()
        }

        suspend fun logo(titleId: Long): List<String> = repository.logo(titleId)

        private var catalogLabels: Map<String, String> = emptyMap()

        private fun enabled(key: String): Boolean =
            key !in prefs.value.homeHidden && HomeRows.group(key) !in prefs.value.homeHidden

        fun removeContinue(titleId: Long) {
            viewModelScope.launch { repository.clearWatchState(titleId) }
        }

        fun reload() {
            reloadPending = true
            if (reloadJob?.isActive == true) return
            reloadJob =
                viewModelScope.launch {
                    personalJob?.cancel()
                    catalogJob?.cancel()
                    do {
                        reloadPending = false
                        loadBaseRows()
                        if (!preparationStarted) {
                            preparationStarted = true
                            // Catalogul salvat este deja vizibil. Reindexarea funcționează și offline.
                            viewModelScope.launch { repository.prepare() }
                        }
                    } while (reloadPending)
                    loadPersonal()
                    loadCatalogs()
                }
        }

        private suspend fun loadBaseRows() {
            loadingMore.values.forEach(Job::cancel)
            loadingMore.clear()
            val publishProgressively = base.isEmpty()
            val rows = mutableListOf<HomeRow>()

            fun add(row: HomeRow?) {
                row ?: return
                rows += row
                if (publishProgressively) {
                    base = rows.toList()
                    publish()
                }
            }

            if (enabled(CONTINUE)) add(continueRow())
            if (enabled(HomeRows.WATCHLIST)) add(newRow(HomeRows.WATCHLIST, "Lista mea"))
            // Primele pagini folosesc același traseu ca filele Filme și Seriale.
            for ((key, title, filter) in specs.take(2)) {
                if (enabled(key)) add(newRow(key, title, filter))
            }
            if (enabled(NEWS)) add(newRow(NEWS, "Nou în playlist"))
            if (enabled(EPISODES)) add(newRow(EPISODES, "Episoade noi la serialele urmărite"))
            for ((key, title, filter) in specs.drop(2)) {
                if (enabled(key)) add(newRow(key, title, filter))
            }
            windows.keys.retainAll(rows.map { it.key }.toSet())
            base = rows
            loaded = true
            publish()
        }

        private suspend fun continueRow(): HomeRow? {
            val cards = repository.continueWatching()
            if (cards.isEmpty()) return null
            return HomeRow(
                CONTINUE,
                "Continuă vizionarea",
                cards,
                null,
                endReached = true,
                continueInfo = repository.continueItems(cards.size).associateBy { it.titleId },
            )
        }

        fun refreshContinueWatching() {
            viewModelScope.launch {
                if (!loaded || !enabled(CONTINUE)) return@launch
                val row = continueRow()
                base = base.filter { it.key != CONTINUE } + listOfNotNull(row)
                publish()
            }
        }

        private fun refreshWatchlist() = refreshRow(HomeRows.WATCHLIST, "Lista mea")

        private fun refreshEpisodeNews() = refreshRow(EPISODES, "Episoade noi la serialele urmărite")

        private fun refreshRow(key: String, title: String) {
            if (!loaded || !enabled(key) || reloadJob?.isActive == true) return
            loadingMore.remove(key)?.cancel()
            viewModelScope.launch {
                val row = newRow(key, title)
                base = base.filter { it.key != key } + listOfNotNull(row)
                publish()
            }
        }

        private suspend fun newRow(key: String, label: String, filter: CatalogFilter? = null): HomeRow? {
            val window = windows[key] ?: PageWindow<HomeCursor, TitleCard>(maxPages = 4)
            window.refresh { fetch(key, filter, it) }
            val cards = window.items
            if (cards.isEmpty()) return null
            windows[key] = window
            return HomeRow(key, label, cards, filter, endReached = !window.hasLater, hasEarlier = window.hasEarlier)
        }

        private suspend fun fetch(
            key: String,
            filter: CatalogFilter?,
            cursor: HomeCursor?,
        ): KeysetPage<HomeCursor, TitleCard> {
            if (filter != null) {
                val page = repository.page(filter, PAGE, (cursor as? HomeCursor.Catalog)?.cursor)
                return KeysetPage(page.items, page.next?.let { HomeCursor.Catalog(it) })
            }
            if (key == HomeRows.WATCHLIST) {
                val items = repository.watchlistPage((cursor as? HomeCursor.Watchlist)?.id ?: 0, PAGE)
                val next = items.lastOrNull()?.id?.takeIf { items.size == PAGE }
                return KeysetPage(items, next?.let { HomeCursor.Watchlist(it) })
            }
            val page = repository.newsPage(key == EPISODES, (cursor as? HomeCursor.News)?.cursor, PAGE)
            return KeysetPage(page.items, page.cursor?.takeUnless { page.endReached }?.let { HomeCursor.News(it) })
        }

        private fun loadPersonal(delayMs: Long = 0) {
            personalJob?.cancel()
            val recommendations = enabled(HomeRows.RECOMMENDATIONS)
            val collections = enabled(HomeRows.COLLECTIONS)
            val becauseSlots = (0..1).filter { enabled(HomeRows.because(it)) }.toSet()
            if (!(recommendations || collections || becauseSlots.isNotEmpty())) return
            personalJob =
                viewModelScope.launch {
                    delay(delayMs)
                    val rows =
                        withContext(Dispatchers.Default) {
                            runCatching {
                                discover.personalRows(
                                    recommendations,
                                    collections,
                                    becauseSlots,
                                )
                            }.getOrNull()
                        }
                    if (rows != null) {
                        personal = rows.map { it.toHomeRow() }
                        publish()
                    }
                }
        }

        private fun loadCatalogs() {
            catalogJob?.cancel()
            if (!enabled(HomeRows.ADDONS)) return
            catalogJob =
                viewModelScope.launch {
                    val hidden = prefs.value.homeHidden
                    val result =
                        withContext(Dispatchers.Default) { runCatching { discover.catalogRows(hidden) }.getOrNull() }
                    if (result != null) {
                        catalogLabels = result.labels
                        catalogs = result.rows.map { it.toHomeRow() }
                        publish()
                    }
                }
        }

        private fun publish() {
            val order = HomeRows.order(prefs.value.homeOrder).withIndex().associate { it.value to it.index }
            val rows =
                (base + personal + catalogs)
                    .filter { enabled(it.key) }
                    .sortedBy { order[it.key] ?: order[HomeRows.group(it.key)] ?: Int.MAX_VALUE }
            mutableState.value = HomeState(loading = !loaded, rows = rows, catalogLabels = catalogLabels)
        }

        fun onItemFocused(key: String, index: Int) {
            val row = base.firstOrNull { it.key == key } ?: return
            val window = windows[key] ?: return
            if (reloadJob?.isActive == true || loadingMore[key]?.isActive == true) return
            val backwards = row.hasEarlier && index < PREFETCH / 2
            if (!backwards && (row.endReached || index < row.items.size - PREFETCH)) return
            loadingMore[key] =
                viewModelScope.launch {
                    window.move(backwards) { fetch(key, row.filter, it) }
                    if (windows[key] !== window || !enabled(key)) return@launch
                    base =
                        base.map {
                            if (it.key == key) {
                                it.copy(
                                    items = window.items.distinctBy { card -> card.id },
                                    endReached = !window.hasLater,
                                    hasEarlier = window.hasEarlier,
                                )
                            } else {
                                it
                            }
                        }
                    publish()
                }
        }

        private fun DiscoverRow.toHomeRow() =
            HomeRow(
                if (key.startsWith("d:")) key else "d:$key",
                title,
                items,
                null,
                endReached = true,
            )

        companion object {
            const val NEWS = HomeRows.NEWS
            const val EPISODES = HomeRows.EPISODES
            const val CONTINUE = HomeRows.CONTINUE
            const val PAGE = 24
            const val PREFETCH = 8
            const val PERSONAL_DEBOUNCE_MS = 3_000L
        }
    }
