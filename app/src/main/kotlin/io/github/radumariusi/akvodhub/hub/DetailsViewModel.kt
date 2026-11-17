package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.Credits
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.catalog.SeriesEpisodes
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.data.db.WatchStateEntity
import io.github.radumariusi.akvodhub.data.discover.DiscoverRepository
import io.github.radumariusi.akvodhub.data.discover.TitleDiscovery
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsState(
    val titleId: Long? = null,
    val loading: Boolean = true,
    val details: TitleDetails? = null,
    val episodes: SeriesEpisodes? = null,
    /** Episoadele se cer încă de la server (Xtream). */
    val loadingEpisodes: Boolean = false,
    val credits: Credits? = null,
    val selectedSeason: Int? = null,
    /** Unde a rămas utilizatorul (pentru „Continuă”). */
    val watch: WatchStateEntity? = null,
    /** Sursa pe care o alege playerul singur (profilul din Setări → Redare). */
    val recommended: SourceInfo? = null,
    /** Colecția, „Recomandate”, „Similare”, alte titluri ale regizorului (doar ce există în playlist). */
    val discovery: TitleDiscovery? = null,
)

/** Pagina de detalii a unui film sau serial: fișa, sursele grupate, episoadele unite, distribuția. */
@HiltViewModel
class DetailsViewModel
    @Inject
    constructor(
        private val repository: HubRepository,
        private val discover: DiscoverRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DetailsState())
        val state: StateFlow<DetailsState> = mutableState.asStateFlow()
        private var job: Job? = null

        init {
            viewModelScope.launch { repository.watchVersion.drop(1).collect { refreshWatch() } }
            viewModelScope.launch {
                repository.catalogVersion.drop(1).collect {
                    val id = mutableState.value.titleId ?: return@collect
                    val details = repository.details(id)
                    val sources = details?.groups.orEmpty().flatMap { group -> group.languages.flatMap { it.sources } }
                    val recommended = repository.playOrder(sources, details?.title?.originalLanguage).firstOrNull()
                    mutableState.update { state ->
                        if (state.titleId == id) state.copy(details = details, recommended = recommended) else state
                    }
                }
            }
            viewModelScope.launch {
                repository.sourcePreferences.drop(1).collect {
                    mutableState.update { state ->
                        val details = state.details ?: return@update state
                        val sources = details.groups.flatMap { group -> group.languages.flatMap { it.sources } }
                        state.copy(
                            recommended = repository.playOrder(sources, details.title.originalLanguage).firstOrNull(),
                        )
                    }
                }
            }
        }

        fun open(titleId: Long) {
            if (mutableState.value.titleId == titleId && mutableState.value.details != null) return
            job?.cancel()
            mutableState.value = DetailsState(titleId = titleId)
            job =
                viewModelScope.launch {
                    val details = repository.details(titleId)
                    val all = details?.groups.orEmpty().flatMap { g -> g.languages.flatMap { it.sources } }
                    val recommended = repository.playOrder(all, details?.title?.originalLanguage).firstOrNull()
                    val watch = repository.watchState(titleId)
                    mutableState.update {
                        it.copy(loading = false, details = details, recommended = recommended, watch = watch)
                    }
                    if (details == null) return@launch
                    launch {
                        val credits = repository.credits(details.title)
                        mutableState.update { it.copy(credits = credits) }
                    }
                    launch {
                        val discovery = runCatching { discover.forTitle(details.title) }.getOrNull()
                        mutableState.update { if (it.titleId == titleId) it.copy(discovery = discovery) else it }
                    }
                    if (details.title.mediaType == MediaType.TV) {
                        // Întâi ce știm deja (instant), apoi lista completă de la server.
                        showEpisodes(repository.episodes(titleId, refresh = false), loadingMore = true)
                        showEpisodes(repository.episodes(titleId, refresh = true), loadingMore = false)
                    }
                }
        }

        private fun showEpisodes(
            episodes: SeriesEpisodes,
            loadingMore: Boolean,
        ) {
            mutableState.update { s ->
                val seasons = episodes.seasons.map { it.number }
                val selected =
                    s.selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull { it > 0 }
                        ?: seasons.firstOrNull()
                s.copy(episodes = episodes, loadingEpisodes = loadingMore, selectedSeason = selected)
            }
        }

        fun selectSeason(season: Int) = mutableState.update { it.copy(selectedSeason = season) }

        /** După player: „Continuă” arată noua poziție (sau episodul următor). */
        fun refreshWatch() {
            val titleId = mutableState.value.titleId ?: return
            viewModelScope.launch {
                val watch = repository.watchState(titleId)
                mutableState.update { if (it.titleId == titleId) it.copy(watch = watch) else it }
            }
        }
    }
