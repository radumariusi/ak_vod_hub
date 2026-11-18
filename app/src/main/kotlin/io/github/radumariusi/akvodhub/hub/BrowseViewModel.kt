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
    private var cursor: CatalogCursor? = null
    private val loadedIds = mutableSetOf<Long>()

    /** Prima încărcare, când ecranul apare. */
    fun start() {
        if (started) return
        started = true
        load(mutableState.value.filter, debounce = false)
    }

    fun reload() {
        if (started) load(mutableState.value.filter, debounce = false)
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
                val page = if (empty) null else repository.page(filter, PAGE)
                val items = page?.items.orEmpty()
                cursor = page?.next
                loadedIds.clear()
                items.mapTo(loadedIds) { it.id }
                mutableState.value = BrowseState(filter, items, loading = false, endReached = cursor == null)
            }
    }

    /** Ultimul element vizibil e [index]: dacă suntem aproape de capăt, aducem pagina următoare. */
    fun onVisible(index: Int) {
        val s = mutableState.value
        if (s.loading || s.endReached || index < s.items.size - PREFETCH) return
        mutableState.update { it.copy(loading = true) }
        job =
            scope.launch {
                val page = repository.page(s.filter, PAGE, checkNotNull(cursor))
                val current = mutableState.value
                cursor = page.next
                mutableState.value =
                    current.copy(
                        items = current.items + page.items.filter { loadedIds.add(it.id) },
                        loading = false,
                        endReached = cursor == null,
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
