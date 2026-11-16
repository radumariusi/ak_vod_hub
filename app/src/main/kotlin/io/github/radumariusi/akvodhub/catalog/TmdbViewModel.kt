package io.github.radumariusi.akvodhub.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.CatalogRepository
import io.github.radumariusi.akvodhub.data.catalog.MatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyState {
    data object Idle : KeyState

    data object Checking : KeyState

    data class Error(
        val message: String,
    ) : KeyState
}

@HiltViewModel
class TmdbViewModel
    @Inject
    constructor(
        private val repository: CatalogRepository,
    ) : ViewModel() {
        val status: StateFlow<MatchStatus?> =
            repository.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val mutableKeyState = MutableStateFlow<KeyState>(KeyState.Idle)
        val keyState: StateFlow<KeyState> = mutableKeyState.asStateFlow()

        fun saveKey(key: String) {
            if (mutableKeyState.value == KeyState.Checking) return
            mutableKeyState.value = KeyState.Checking
            viewModelScope.launch {
                mutableKeyState.value =
                    try {
                        repository.saveTmdbKey(key)
                        KeyState.Idle
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        KeyState.Error(e.message ?: "Nu am putut verifica cheia.")
                    }
            }
        }

        fun clearKey() = repository.clearTmdbKey()

        fun matchNow() = repository.matchNow()

        fun retryUnmatched() {
            viewModelScope.launch { repository.retryUnmatched() }
        }
    }
