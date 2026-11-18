package io.github.radumariusi.akvodhub.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.parser.catalog.KeysetPage
import io.github.radumariusi.akvodhub.parser.catalog.PageWindow
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
        private var window = PageWindow<Long, TitleCard>()

        init {
            reload()
            viewModelScope.launch { repository.watchVersion.drop(1).collect { reload() } }
            viewModelScope.launch { repository.catalogVersion.drop(1).collect { reload() } }
        }

        private fun reload() {
            job?.cancel()
            mutableState.value = mutableState.value.copy(loading = true)
            job =
                viewModelScope.launch {
                    window.refresh(::fetch)
                    publish()
                }
        }

        private suspend fun fetch(after: Long?): KeysetPage<Long, TitleCard> {
            val items = repository.watchlistPage(after ?: 0, PAGE)
            return KeysetPage(items, items.lastOrNull()?.id?.takeIf { items.size == PAGE })
        }

        private fun publish() {
            mutableState.value =
                WatchlistState(
                    window.items.distinctBy { it.id },
                    loading = false,
                    endReached = !window.hasLater,
                )
        }

        fun onVisible(first: Int, last: Int) {
            val old = mutableState.value
            if (old.loading || first < 0 || old.items.isEmpty()) return
            val backwards = window.hasEarlier && first < 8
            if (!backwards && (old.endReached || last < old.items.size - 18)) return
            mutableState.value = old.copy(loading = true)
            job =
                viewModelScope.launch {
                    window.move(backwards, ::fetch)
                    publish()
                }
        }

        private companion object {
            const val PAGE = 48
        }
    }
