package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un rând de pe Acasă. [filter] null = rând special („Continuă vizionarea”), fără paginare. */
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
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = mutableState.asStateFlow()

        private val loadingMore = mutableSetOf<String>()

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
        }

        fun reload() {
            viewModelScope.launch {
                val keep = mutableState.value.rows.associate { it.key to it.items.size }
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
                mutableState.value = HomeState(loading = false, rows = rows)
            }
        }

        /** Focusul a ajuns la [index] în rândul [key]: dacă e aproape de capăt, aducem pagina următoare. */
        fun onItemFocused(
            key: String,
            index: Int,
        ) {
            val row = mutableState.value.rows.firstOrNull { it.key == key } ?: return
            val filter = row.filter ?: return
            if (row.endReached || index < row.items.size - PREFETCH || !loadingMore.add(key)) return
            viewModelScope.launch {
                try {
                    val more = repository.page(filter, PAGE, row.items.size)
                    mutableState.update { s ->
                        s.copy(
                            rows =
                                s.rows.map {
                                    if (it.key != key) {
                                        it
                                    } else {
                                        val known = it.items.mapTo(HashSet()) { c -> c.id }
                                        it.copy(
                                            items = it.items + more.filter { c -> c.id !in known },
                                            endReached = more.size < PAGE,
                                        )
                                    }
                                },
                        )
                    }
                } finally {
                    loadingMore.remove(key)
                }
            }
        }

        companion object {
            const val CONTINUE = "continue"
            const val PAGE = 24
            const val PREFETCH = 8
        }
    }
