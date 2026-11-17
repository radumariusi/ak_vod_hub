package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistState(
    val items: List<TitleCard> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
)

@HiltViewModel
class WatchlistViewModel
    @Inject
    constructor(private val repository: HubRepository) : ViewModel() {
        private val mutableState = MutableStateFlow(WatchlistState())
        val state = mutableState.asStateFlow()
        private var job: Job? = null

        init {
            reload()
            viewModelScope.launch { repository.watchVersion.drop(1).collect { reload() } }
            viewModelScope.launch { repository.catalogVersion.drop(1).collect { reload() } }
        }

        private fun reload() {
            job?.cancel()
            val keep = maxOf(PAGE, mutableState.value.items.size)
            job =
                viewModelScope.launch {
                    val items = mutableListOf<TitleCard>()
                    var more: List<TitleCard>
                    do {
                        more = repository.watchlistPage(items.lastOrNull()?.id ?: 0, PAGE)
                        items += more
                    } while (more.size == PAGE && items.size < keep)
                    mutableState.value = WatchlistState(items, loading = false, endReached = more.size < PAGE)
                }
        }

        fun onVisible(index: Int) {
            val old = mutableState.value
            if (old.loading || old.endReached || index < old.items.size - 18) return
            mutableState.value = old.copy(loading = true)
            job =
                viewModelScope.launch {
                    val more = repository.watchlistPage(old.items.lastOrNull()?.id ?: 0, PAGE)
                    mutableState.value =
                        old.copy(items = old.items + more, loading = false, endReached = more.size < PAGE)
                }
        }

        private companion object {
            const val PAGE = 48
        }
    }
