package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.Credits
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.catalog.SeriesEpisodes
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.data.db.EpisodeProgressEntity
import io.github.radumariusi.akvodhub.data.db.WatchStateEntity
import io.github.radumariusi.akvodhub.data.discover.DiscoverRepository
import io.github.radumariusi.akvodhub.data.discover.TitleDiscovery
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.player.EpisodeOrder
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
    val logoPath: String? = null,
    val inWatchlist: Boolean = false,
    val watched: Boolean = false,
    val changingLibrary: Boolean = false,
    /** Unde a rămas utilizatorul (pentru „Continuă”). */
    val watch: WatchStateEntity? = null,
    val episodeProgress: Map<Pair<Int, Int>, EpisodeProgressEntity> = emptyMap(),
    /** Sursa pe care o alege playerul singur (profilul din Setări → Redare). */
    val recommended: SourceInfo? = null,
    /** Colecția, „Recomandate”, „Similare”, alte titluri ale regizorului (doar ce există în playlist). */
    val discovery: TitleDiscovery? = null,
) {
    val nextEpisode: MergedEpisode?
        get() {
            val seasons = episodes?.seasons ?: return null
            val available = EpisodeOrder.linear(seasons)
            val resume = watch?.let { w -> EpisodeOrder.find(seasons, w.season, w.episode) }
            if (resume != null) return resume
            val last = episodeProgress.values.maxByOrNull { it.updatedAt }
            val candidate = last?.let { saved ->
                if (saved.finished) EpisodeOrder.next(seasons, saved.season, saved.episode)
                else EpisodeOrder.find(seasons, saved.season, saved.episode)
            }
            return candidate ?: available.firstOrNull { it.number?.let { number -> episodeProgress[it.season to number]?.finished } != true }
        }
}

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
                    if (details?.title?.mediaType == MediaType.TV) {
                        val episodes = repository.episodes(id, refresh = false)
                        if (mutableState.value.titleId == id) showEpisodes(episodes, loadingMore = false)
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
            val current = mutableState.value.details?.title
            if (mutableState.value.titleId == titleId && current != null) return
            job?.cancel()
            mutableState.value = DetailsState(titleId = titleId)
            job =
                viewModelScope.launch {
                    val details = repository.details(titleId)
                    val all = details?.groups.orEmpty().flatMap { g -> g.languages.flatMap { it.sources } }
                    val recommended = repository.playOrder(all, details?.title?.originalLanguage).firstOrNull()
                    val watch = repository.watchState(titleId)
                    val progress = if (details?.title?.mediaType == MediaType.TV) repository.episodeProgress(titleId) else emptyList()
                    val listed = repository.inWatchlist(titleId)
                    val watched = repository.isWatched(titleId)
                    mutableState.update {
                        it.copy(
                            loading = false,
                            details = details,
                            recommended = recommended,
                            watch = watch,
                            episodeProgress = progress.associateBy { it.season to it.episode },
                            inWatchlist = listed,
                            watched = watched,
                        )
                    }
                    if (details == null) return@launch
                    launch {
                        val logo = repository.logo(titleId)
                        mutableState.update { if (it.titleId == titleId) it.copy(logoPath = logo) else it }
                    }
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
                    s.selectedSeason?.takeIf { it in seasons }
                        ?: s.copy(episodes = episodes).nextEpisode?.season?.takeIf { it in seasons }
                        ?: seasons.firstOrNull { it > 0 }
                        ?: seasons.firstOrNull()
                s.copy(episodes = episodes, loadingEpisodes = loadingMore, selectedSeason = selected)
            }
        }

        fun sourceReason(source: SourceInfo, originalLanguage: String?): String = repository.sourceReason(source, originalLanguage)

        suspend fun episodeOverview(episode: MergedEpisode): String? {
            val title = state.value.details?.title ?: return null
            val number = episode.number ?: return null
            return repository.episodeOverview(title, episode.season, number)
        }

        suspend fun trailer(titleId: Long): String? = repository.trailer(titleId)

        fun toggleWatchlist() = changeLibrary { id -> repository.setInWatchlist(id, !mutableState.value.inWatchlist) }

        fun toggleWatched() = changeLibrary { id -> repository.markWatched(id, !mutableState.value.watched) }

        private fun changeLibrary(action: suspend (Long) -> Unit) {
            val state = mutableState.value
            val id = state.titleId ?: return
            if (state.changingLibrary) return
            mutableState.update { it.copy(changingLibrary = true) }
            viewModelScope.launch {
                try {
                    action(id)
                    updateWatch(id)
                } finally {
                    mutableState.update { if (it.titleId == id) it.copy(changingLibrary = false) else it }
                }
            }
        }

        fun selectSeason(season: Int) = mutableState.update { it.copy(selectedSeason = season) }

        /** După player: „Continuă” arată noua poziție (sau episodul următor). */
        fun refreshWatch() {
            val titleId = mutableState.value.titleId ?: return
            viewModelScope.launch { updateWatch(titleId) }
        }

        private suspend fun updateWatch(titleId: Long) {
            val watch = repository.watchState(titleId)
            val listed = repository.inWatchlist(titleId)
            val watched = repository.isWatched(titleId)
            val progress = if (state.value.details?.title?.mediaType == MediaType.TV) {
                repository.episodeProgress(titleId).associateBy { it.season to it.episode }
            } else {
                emptyMap()
            }
            mutableState.update {
                if (it.titleId == titleId) it.copy(watch = watch, episodeProgress = progress, inWatchlist = listed, watched = watched) else it
            }
        }
    }
