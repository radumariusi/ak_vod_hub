package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.ActorRepository
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.parser.catalog.KeysetPage
import io.github.radumariusi.akvodhub.parser.catalog.PageWindow
import kotlinx.coroutines.CancellationException
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPerson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActorState(
    val personId: Long? = null,
    val items: List<TitleCard> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ActorViewModel
    @Inject
    constructor(private val repository: ActorRepository, hub: HubRepository) : ViewModel() {
        private val mutableState = MutableStateFlow(ActorState())
        val state = mutableState.asStateFlow()
        private val mutableProfile = MutableStateFlow<TmdbPerson?>(null)

        /** Biografia persoanei deschise (separat de filmografie, ca să nu întârzie grila). */
        val profile = mutableProfile.asStateFlow()
        private var profileJob: Job? = null
        private var job: Job? = null
        private var window = PageWindow<Long, TitleCard>()

        init {
            viewModelScope.launch {
                hub.catalogVersion
                    .drop(
                        1,
                    ).collect { mutableState.value.personId?.let { open(it, true) } }
            }
            viewModelScope.launch {
                hub.posterProgress.collect { value ->
                    value?.let { (id, progress) ->
                        mutableState.update { state ->
                            state.copy(
                                items =
                                    state.items.map {
                                        if (it.id == id) it.copy(watchProgress = progress) else it
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun open(personId: Long, reload: Boolean = false, restoreThrough: Long? = null) {
            if (!reload && mutableState.value.personId == personId) return
            if (mutableProfile.value?.id != personId) {
                profileJob?.cancel()
                mutableProfile.value = null
                profileJob = viewModelScope.launch { mutableProfile.value = repository.profile(personId) }
            }
            job?.cancel()
            mutableState.value = ActorState(personId)
            job =
                viewModelScope.launch {
                    try {
                        repository.prepare(personId)
                        val fresh = PageWindow<Long, TitleCard>()
                        do {
                            fresh.move { fetch(personId, it) }
                        } while (restoreThrough != null && fresh.hasLater && fresh.items.last().id < restoreThrough)
                        window = fresh
                        mutableState.value =
                            ActorState(personId, fresh.items, loading = false, endReached = !fresh.hasLater)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mutableState.value =
                            ActorState(
                                personId,
                                loading = false,
                                error =
                                    "Filmografia nu a putut fi încărcată. " +
                                        "Verifică rețeaua și cheia TMDB din Setări.",
                            )
                    }
                }
        }

        private suspend fun fetch(personId: Long, after: Long?): KeysetPage<Long, TitleCard> {
            val items = repository.page(personId, after ?: 0, PAGE)
            return KeysetPage(items, items.lastOrNull()?.id?.takeIf { items.size == PAGE })
        }

        fun onVisible(first: Int, last: Int) {
            val state = mutableState.value
            val id = state.personId ?: return
            if (state.loading || first < 0 || state.items.isEmpty()) return
            val backwards = window.hasEarlier && first < 8
            if (!backwards && (state.endReached || last < state.items.size - 18)) return
            mutableState.update { it.copy(loading = true) }
            job =
                viewModelScope.launch {
                    try {
                        window.move(backwards) { fetch(id, it) }
                        mutableState.update {
                            it.copy(
                                items = window.items.distinctBy { card -> card.id },
                                loading = false,
                                endReached = !window.hasLater,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mutableState.update {
                            it.copy(
                                loading = false,
                                endReached = true,
                                error = "Lista nu a putut fi continuată.",
                            )
                        }
                    }
                }
        }

        private companion object {
            const val PAGE = 48
        }
    }
