package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.parser.catalog.BrowseFilters
import io.github.radumariusi.akvodhub.parser.catalog.CatalogCursor
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.FilterPreset
import io.github.radumariusi.akvodhub.parser.catalog.KeysetPage
import io.github.radumariusi.akvodhub.parser.catalog.PageWindow
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseState(
    val filter: CatalogFilter,
    val items: List<TitleCard> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val hasEarlier: Boolean = false,
)

/**
 * O grilă paginată de titluri cu filtre (Filme, Seriale, Căutare). Schimbarea filtrelor anulează
 * încărcarea în curs; textul căutat așteaptă [TYPING_DELAY_MS] după ultima tastă.
 */
class Browser(
    private val scope: CoroutineScope,
    private val repository: HubRepository,
    initial: CatalogFilter,
    /** Căutarea nu arată nimic până nu scrii ceva sau alegi un filtru. */
    private val needsInput: Boolean,
    private val preferences: PlaybackSettings? = null,
) {
    private val type = initial.type
    private val mutableState = MutableStateFlow(BrowseState(savedFilter() ?: initial))

    fun presets(): List<FilterPreset> =
        type?.let { BrowseFilters.decodePresets(it, preferences?.state?.value?.browseFilters?.get(BrowseFilters.presets(it))) }.orEmpty()

    fun savePreset(name: String, filter: CatalogFilter) {
        val media = type ?: return
        val label = BrowseFilters.name(name)
        if (label.isEmpty()) return
        val presets = listOf(FilterPreset(label, filter)) + presets().filterNot { it.name.equals(label, ignoreCase = true) }
        preferences?.setBrowseFilter(BrowseFilters.presets(media), BrowseFilters.encodePresets(presets))
    }

    fun removePreset(name: String) {
        val media = type ?: return
        preferences?.setBrowseFilter(BrowseFilters.presets(media), BrowseFilters.encodePresets(presets().filterNot { it.name == name }))
    }

    private fun savedFilter(): CatalogFilter? =
        type?.let { BrowseFilters.decode(it, preferences?.state?.value?.browseFilters?.get(BrowseFilters.current(it))) }

    fun restoreFilter() {
        val restored = savedFilter() ?: return
        if (restored == state.value.filter) return
        if (started) load(restored, debounce = false) else mutableState.value = BrowseState(restored)
    }
    val state: StateFlow<BrowseState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var started = false
    private var window = PageWindow<CatalogCursor, TitleCard>()

    /** Prima încărcare, când ecranul apare. */
    fun start() {
        if (started) return
        started = true
        load(mutableState.value.filter, debounce = false)
    }

    fun reload() {
        if (!started) return
        val filter = mutableState.value.filter
        if (mutableState.value.loading || window.items.isEmpty()) {
            load(filter, debounce = false)
            return
        }
        mutableState.update { it.copy(loading = true) }
        job = scope.launch {
            window.refresh { fetch(filter, it) }
            publishWindow()
        }
    }

    /** Schimbă doar cardul deja încărcat, fără resetarea paginării sau a focusului. */
    fun updateProgress(titleId: Long, progress: Double) {
        mutableState.update { state ->
            val index = state.items.indexOfFirst { it.id == titleId }
            if (index < 0) {
                state
            } else {
                state.copy(
                    items =
                        state.items.toMutableList().apply {
                            this[index] = this[index].copy(watchProgress = progress)
                        },
                )
            }
        }
    }

    fun setFilter(filter: CatalogFilter) {
        val old = mutableState.value.filter
        if (filter == old) return
        load(filter, debounce = filter.text != old.text)
        type?.let { preferences?.setBrowseFilter(BrowseFilters.current(it), BrowseFilters.encode(filter)) }
    }

    private fun load(
        filter: CatalogFilter,
        debounce: Boolean,
    ) {
        job?.cancel()
        mutableState.update { it.copy(filter = filter, loading = true) }
        job =
            scope.launch {
                if (debounce) delay(TYPING_DELAY_MS)
                val empty = needsInput && filter.text.isBlank() && !filter.hasFilters
                val fresh = PageWindow<CatalogCursor, TitleCard>()
                if (!empty) fresh.move { fetch(filter, it) }
                window = fresh
                mutableState.value = BrowseState(filter, fresh.items, loading = false, endReached = empty || !fresh.hasLater)
            }
    }

    private suspend fun fetch(filter: CatalogFilter, after: CatalogCursor?): KeysetPage<CatalogCursor, TitleCard> {
        val page = repository.page(filter, PAGE, after)
        return KeysetPage(page.items, page.next)
    }

    fun onVisible(first: Int, last: Int) {
        val s = mutableState.value
        if (s.loading || s.items.isEmpty() || first < 0) return
        val backwards = s.hasEarlier && first < PREFETCH / 2
        if (!backwards && (s.endReached || last < s.items.size - PREFETCH)) return
        mutableState.update { it.copy(loading = true) }
        job =
            scope.launch {
                window.move(backwards) { fetch(s.filter, it) }
                publishWindow()
            }
    }

    private fun publishWindow() {
        mutableState.update {
            it.copy(
                items = window.items.distinctBy { card -> card.id },
                loading = false,
                endReached = !window.hasLater,
                hasEarlier = window.hasEarlier,
            )
        }
    }

    companion object {
        const val PAGE = 48
        const val PREFETCH = 18
        const val TYPING_DELAY_MS = 300L
    }
}

/** Grilele Filme, Seriale și Căutare, fiecare cu filtrele ei (păstrate cât timp aplicația e deschisă). */
@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        private val repository: HubRepository,
        private val preferences: PlaybackSettings,
    ) : ViewModel() {
        val movies = Browser(viewModelScope, repository, CatalogFilter(type = MediaType.MOVIE), needsInput = false, preferences = preferences)
        val series = Browser(viewModelScope, repository, CatalogFilter(type = MediaType.TV), needsInput = false, preferences = preferences)
        val search = Browser(viewModelScope, repository, CatalogFilter(), needsInput = true)

        private val mutableLanguages = MutableStateFlow<List<String>>(emptyList())

        /** Limbile audio din playlist-uri, pentru filtrul „Limbă”. */
        val languages: StateFlow<List<String>> = mutableLanguages.asStateFlow()

        init {
            viewModelScope.launch {
                preferences.state.drop(1).collect {
                    movies.restoreFilter()
                    series.restoreFilter()
                }
            }
            viewModelScope.launch { mutableLanguages.value = repository.languages() }
            viewModelScope.launch {
                repository.watchVersion.drop(1).collect {
                    listOf(movies, series, search).filter { it.state.value.filter.library !=
                        io.github.radumariusi.akvodhub.parser.catalog.LibraryFilter.ALL }.forEach(Browser::reload)
                }
            }
            viewModelScope.launch {
                repository.posterProgress.collect { value ->
                    value?.let { (id, progress) ->
                        listOf(movies, series, search).forEach { it.updateProgress(id, progress) }
                    }
                }
            }
            viewModelScope.launch {
                repository.catalogVersion.drop(1).collect {
                    mutableLanguages.value = repository.languages()
                    listOf(movies, series, search).forEach(Browser::reload)
                }
            }
        }
    }
