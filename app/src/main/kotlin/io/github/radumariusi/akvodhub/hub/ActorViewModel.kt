package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.ActorRepository
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import kotlinx.coroutines.CancellationException
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
        private var job: Job? = null

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
            job?.cancel()
            mutableState.value = ActorState(personId)
            job =
                viewModelScope.launch {
                    try {
                        repository.prepare(personId)
                        var page = repository.page(personId, 0, PAGE)
                        val items = page.toMutableList()
                        while (restoreThrough != null && page.size == PAGE && items.last().id < restoreThrough) {
                            page = repository.page(personId, items.last().id, PAGE)
                            items += page
                        }
                        mutableState.value =
                            ActorState(personId, items, loading = false, endReached = page.size < PAGE)
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

        fun onVisible(index: Int) {
            val state = mutableState.value
            val id = state.personId ?: return
            if (state.loading || state.endReached || index < state.items.size - 18) return
            mutableState.update { it.copy(loading = true) }
            job =
                viewModelScope.launch {
                    try {
                        val more = repository.page(id, state.items.lastOrNull()?.id ?: 0, PAGE)
                        mutableState.update {
                            it.copy(
                                items = it.items + more,
                                loading = false,
                                endReached =
                                    more.size < PAGE,
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
