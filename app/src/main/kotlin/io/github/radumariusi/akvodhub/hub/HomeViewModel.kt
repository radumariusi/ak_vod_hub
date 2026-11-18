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
import io.github.radumariusi.akvodhub.parser.catalog.NewsCursor
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
    val newsCursor: NewsCursor? = null,
    val catalogCursor: CatalogCursor? = null,
    val continueInfo: Map<Long, ContinueItem> = emptyMap(),
)

data class HomeState(
    val loading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
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

        private val loadingMore = mutableSetOf<String>()

        /** Rândurile din catalog (paginate) și „Continuă vizionarea”. */
        private var base: List<HomeRow> = emptyList()

        /** Recomandări personale (din istoric) și cataloagele addon-urilor; se încarcă separat, fără să țină Acasă. */
        private var personal: List<HomeRow> = emptyList()
        private var catalogs: List<HomeRow> = emptyList()
        private var loaded = false
        private var reloadJob: Job? = null
        private var personalJob: Job? = null
        private var catalogJob: Job? = null

        private val specs: List<Triple<String, String, CatalogFilter>> =
            listOf(
                Triple("movies", "Filme populare", CatalogFilter(type = MediaType.MOVIE)),
                Triple("series", "Seriale populare", CatalogFilter(type = MediaType.TV)),
                Triple("rated", "Cele mai apreciate", CatalogFilter(sort = CatalogSort.RATING)),
            ) + Genres.home.map { Triple("genre:${it.key}", it.label, CatalogFilter(genre = it)) }

        init {
            viewModelScope.launch {
                repository.prepare()
                reload()
            }
            // După fiecare reindexare (import, potrivire TMDB, playlist șters), rândurile se reîncarcă.
            viewModelScope.launch {
                repository.catalogVersion.drop(1).collect { reload() }
            }
            viewModelScope.launch {
                repository.watchVersion.drop(1).collect {
                    refreshContinueWatching()
                    refreshEpisodeNews()
                    refreshWatchlist()
                    loadPersonal(delayMs = PERSONAL_DEBOUNCE_MS)
                }
            }
            viewModelScope.launch {
                discover.installedAddons.drop(1).collect { loadCatalogs() }
            }
            viewModelScope.launch {
                prefs.map { it.homeOrder to it.homeHidden }.distinctUntilChanged().drop(1).collect { reload() }
            }
        }

        suspend fun logo(titleId: Long): String? = repository.logo(titleId)

        private fun enabled(key: String): Boolean = HomeRows.group(key) !in prefs.value.homeHidden

        fun removeContinue(titleId: Long) {
            viewModelScope.launch { repository.clearWatchState(titleId) }
        }

        fun reload() {
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch {
                val keep = base.associate { it.key to it.items.size }
                val rows = mutableListOf<HomeRow>()
                if (enabled(CONTINUE)) continueRow()?.let { rows += it }
                if (enabled(HomeRows.WATCHLIST)) watchlistRow()?.let { rows += it }
                for (key in listOf(NEWS, EPISODES).filter(::enabled)) {
                    newsRow(key, maxOf(PAGE, keep[key] ?: 0))?.let { rows += it }
                }
                for ((key, title, filter) in specs) {
                    if (!enabled(key)) continue
                    // Păstrăm câte titluri erau deja încărcate, ca rândul să nu „sară” sub focus.
                    val size = maxOf(PAGE, keep[key] ?: 0)
                    val page = repository.page(filter, size)
                    if (page.items.isNotEmpty()) {
                        rows += HomeRow(key, title, page.items, filter, page.next == null, catalogCursor = page.next)
                    }
                }
                base = rows
                loaded = true
                publish()
                // Disponibilitatea s-a putut schimba: refacem și rândurile de descoperire.
                loadPersonal()
                loadCatalogs()
            }
        }

        private suspend fun continueRow(): HomeRow? {
            val cards = repository.continueWatching()
            if (cards.isEmpty()) return null
            return HomeRow(
                CONTINUE, "Continuă vizionarea", cards, null, endReached = true,
                continueInfo = repository.continueItems(cards.size).associateBy { it.titleId },
            )
        }

        private suspend fun watchlistRow(): HomeRow? {
            val cards = repository.watchlistPage(0, PAGE)
            return cards.takeIf { it.isNotEmpty() }?.let {
                HomeRow(HomeRows.WATCHLIST, "Lista mea", it, null, endReached = true)
            }
        }

        /** După player: actualizează doar rândul „Continuă vizionarea”. */
        fun refreshContinueWatching() {
            viewModelScope.launch {
                if (!loaded || !enabled(CONTINUE)) return@launch
                val row = continueRow()
                base = base.filter { it.key != CONTINUE } + listOfNotNull(row)
                publish()
            }
        }

        private fun refreshWatchlist() {
            viewModelScope.launch {
                if (!loaded || !enabled(HomeRows.WATCHLIST)) return@launch
                val row = watchlistRow()
                base = base.filter { it.key != HomeRows.WATCHLIST } + listOfNotNull(row)
                publish()
            }
        }

        private suspend fun newsRow(key: String, desired: Int): HomeRow? {
            var cursor: NewsCursor? = null
            val cards = mutableListOf<TitleCard>()
            var end: Boolean
            do {
                val page = repository.newsPage(key == EPISODES, cursor, PAGE)
                cards += page.items
                cursor = page.cursor
                end = page.endReached
            } while (!end && cards.size < desired)
            if (cards.isEmpty()) return null
            return HomeRow(
                key,
                if (key == NEWS) "Nou în playlist" else "Episoade noi la serialele urmărite",
                cards,
                null,
                end,
                cursor,
            )
        }

        private fun refreshEpisodeNews() {
            viewModelScope.launch {
                if (!loaded || !enabled(EPISODES)) return@launch
                val desired = base.firstOrNull { it.key == EPISODES }?.items?.size ?: PAGE
                val row = newsRow(EPISODES, desired)
                val rest = base.filter { it.key != EPISODES }
                base = if (row == null) rest else rest + row
                publish()
            }
        }

        private fun loadPersonal(delayMs: Long = 0) {
            personalJob?.cancel()
            val recommendations = enabled(HomeRows.RECOMMENDATIONS)
            val collections = enabled(HomeRows.COLLECTIONS)
            val because = enabled(HomeRows.BECAUSE)
            if (!(recommendations || collections || because)) return
            personalJob =
                viewModelScope.launch {
                    delay(delayMs)
                    val rows = withContext(Dispatchers.Default) { runCatching { discover.personalRows(recommendations, collections, because) }.getOrNull() }
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
                    val rows = withContext(Dispatchers.Default) { runCatching { discover.catalogRows() }.getOrNull() }
                    if (rows != null) {
                        catalogs = rows.map { it.toHomeRow() }
                        publish()
                    }
                }
        }

        private fun publish() {
            val order = HomeRows.order(prefs.value.homeOrder).withIndex().associate { it.value to it.index }
            val rows = (base + personal + catalogs).filter { enabled(it.key) }
                .sortedBy { order[HomeRows.group(it.key)] ?: Int.MAX_VALUE }
            mutableState.value = HomeState(loading = !loaded, rows = rows)
        }

        /** Focusul a ajuns la [index] în rândul [key]: dacă e aproape de capăt, aducem pagina următoare. */
        fun onItemFocused(
            key: String,
            index: Int,
        ) {
            val row = base.firstOrNull { it.key == key } ?: return
            val news = key == NEWS || key == EPISODES
            val filter = row.filter
            if (!news && filter == null) return
            if (row.endReached || index < row.items.size - PREFETCH || !loadingMore.add(key)) return
            viewModelScope.launch {
                try {
                    val newsPage = if (news) repository.newsPage(key == EPISODES, row.newsCursor, PAGE) else null
                    val catalogPage = filter?.let { repository.page(it, PAGE, checkNotNull(row.catalogCursor)) }
                    val more = newsPage?.items ?: checkNotNull(catalogPage).items
                    base =
                        base.map {
                            if (it !== row) {
                                it
                            } else {
                                val known = it.items.mapTo(HashSet()) { c -> c.id }
                                it.copy(
                                    items = it.items + more.filter { c -> c.id !in known },
                                    endReached = newsPage?.endReached ?: (catalogPage?.next == null),
                                    newsCursor = newsPage?.cursor,
                                    catalogCursor = catalogPage?.next,
                                )
                            }
                        }
                    publish()
                } finally {
                    loadingMore.remove(key)
                }
            }
        }

        private fun DiscoverRow.toHomeRow() = HomeRow("d:$key", title, items, null, endReached = true)

        companion object {
            const val NEWS = HomeRows.NEWS
            const val EPISODES = HomeRows.EPISODES
            const val CONTINUE = HomeRows.CONTINUE
            const val PAGE = 24
            const val PREFETCH = 8
            const val PERSONAL_DEBOUNCE_MS = 3_000L
        }
    }
