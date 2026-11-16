package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.Credits
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.catalog.SeriesEpisodes
import io.github.radumariusi.akvodhub.data.catalog.TitleDetails
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/** Pagina de detalii a unui film sau serial: fișa, sursele grupate, episoadele unite, distribuția. */
@HiltViewModel
class DetailsViewModel
    @Inject
    constructor(
        private val repository: HubRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DetailsState())
        val state: StateFlow<DetailsState> = mutableState.asStateFlow()
        private var job: Job? = null

        fun open(titleId: Long) {
            if (mutableState.value.titleId == titleId && mutableState.value.details != null) return
            job?.cancel()
            mutableState.value = DetailsState(titleId = titleId)
            job =
                viewModelScope.launch {
                    val details = repository.details(titleId)
                    mutableState.update { it.copy(loading = false, details = details) }
                    if (details == null) return@launch
                    launch {
                        val credits = repository.credits(details.title)
                        mutableState.update { it.copy(credits = credits) }
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

        suspend fun streamUrl(entryId: Long): String? = repository.streamUrl(entryId)
    }
