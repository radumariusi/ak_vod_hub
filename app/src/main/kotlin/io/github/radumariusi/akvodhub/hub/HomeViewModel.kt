package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.discover.DiscoverRepository
import io.github.radumariusi.akvodhub.data.discover.DiscoverRow
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
        private val mutableState = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = mutableState.asStateFlow()

        private val loadingMore = mutableSetOf<String>()

        /** Rândurile din catalog (paginate) și „Continuă vizionarea”. */
        private var base: List<HomeRow> = emptyList()

        /** Recomandări personale (din istoric) și cataloagele addon-urilor; se încarcă separat, fără să țină Acasă. */
        private var personal: List<HomeRow> = emptyList()
        private var catalogs: List<HomeRow> = emptyList()
        private var loaded = false
        private var personalJob: Job? = null
        private var catalogJob: Job? = null

        private val specs: List<Triple<String, String, CatalogFilter>> =
            listOf(
                Triple("recent", "Adăugate recent", CatalogFilter(sort = CatalogSort.RECENT)),
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
                    loadPersonal(delayMs = PERSONAL_DEBOUNCE_MS)
                }
            }
            viewModelScope.launch {
                discover.installedAddons.drop(1).collect { loadCatalogs() }
            }
        }

        suspend fun logo(titleId: Long): String? = repository.logo(titleId)

        fun reload() {
            viewModelScope.launch {
                val keep = base.associate { it.key to it.items.size }
                val rows = mutableListOf<HomeRow>()
                val watching = repository.continueWatching()
                if (watching.isNotEmpty()) {
                    rows += HomeRow(CONTINUE, "Continuă vizionarea", watching, null, endReached = true)
                }
                for ((key, title, filter) in specs) {
                    // Păstrăm câte titluri erau deja încărcate, ca rândul să nu „sară” sub focus.
                    val size = maxOf(PAGE, keep[key] ?: 0)
                    val items = repository.page(filter, size, 0)
                    if (items.isNotEmpty()) rows += HomeRow(key, title, items, filter, items.size < size)
                }
                base = rows
                loaded = true
                publish()
                // Disponibilitatea s-a putut schimba: refacem și rândurile de descoperire.
                loadPersonal()
                loadCatalogs()
            }
        }

        /** După player: actualizează doar rândul „Continuă vizionarea”. */
        fun refreshContinueWatching() {
            viewModelScope.launch {
                if (!loaded) return@launch
                val watching = repository.continueWatching()
                val rest = base.filter { it.key != CONTINUE }
                val row = HomeRow(CONTINUE, "Continuă vizionarea", watching, null, endReached = true)
                base = if (watching.isEmpty()) rest else listOf(row) + rest
                publish()
            }
        }

        private fun loadPersonal(delayMs: Long = 0) {
            personalJob?.cancel()
            personalJob =
                viewModelScope.launch {
                    delay(delayMs)
                    val rows = withContext(Dispatchers.Default) { runCatching { discover.personalRows() }.getOrNull() }
                    if (rows != null) {
                        personal = rows.map { it.toHomeRow() }
                        publish()
                    }
                }
        }

        private fun loadCatalogs() {
            catalogJob?.cancel()
            catalogJob =
                viewModelScope.launch {
                    val rows = withContext(Dispatchers.Default) { runCatching { discover.catalogRows() }.getOrNull() }
                    if (rows != null) {
                        catalogs = rows.map { it.toHomeRow() }
                        publish()
                    }
                }
        }

        /** Ordinea pe Acasă: Continuă, recomandările personale, cataloagele addon-urilor, apoi restul. */
        private fun publish() {
            val cont = base.filter { it.key == CONTINUE }
            val rest = base.filter { it.key != CONTINUE }
            mutableState.value = HomeState(loading = !loaded, rows = cont + personal + catalogs + rest)
        }

        /** Focusul a ajuns la [index] în rândul [key]: dacă e aproape de capăt, aducem pagina următoare. */
        fun onItemFocused(
            key: String,
            index: Int,
        ) {
            val row = base.firstOrNull { it.key == key } ?: return
            val filter = row.filter ?: return
            if (row.endReached || index < row.items.size - PREFETCH || !loadingMore.add(key)) return
            viewModelScope.launch {
                try {
                    val more = repository.page(filter, PAGE, row.items.size)
                    base =
                        base.map {
                            if (it.key != key) {
                                it
                            } else {
                                val known = it.items.mapTo(HashSet()) { c -> c.id }
                                it.copy(
                                    items = it.items + more.filter { c -> c.id !in known },
                                    endReached = more.size < PAGE,
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
            const val CONTINUE = "continue"
            const val PAGE = 24
            const val PREFETCH = 8
            const val PERSONAL_DEBOUNCE_MS = 3_000L
        }
    }
