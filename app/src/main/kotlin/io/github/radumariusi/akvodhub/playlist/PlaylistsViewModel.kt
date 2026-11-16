package io.github.radumariusi.akvodhub.playlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.playlist.LibraryTotals
import io.github.radumariusi.akvodhub.data.playlist.PlaylistRepository
import io.github.radumariusi.akvodhub.data.playlist.PlaylistSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddPlaylistState {
    data object Idle : AddPlaylistState

    data object Saving : AddPlaylistState

    data object Done : AddPlaylistState

    data class Error(
        val message: String,
    ) : AddPlaylistState
}

@HiltViewModel
class PlaylistsViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
    ) : ViewModel() {
        val playlists: StateFlow<List<PlaylistSummary>> =
            repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val totals: StateFlow<LibraryTotals?> =
            repository.totals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val mutableAddState = MutableStateFlow<AddPlaylistState>(AddPlaylistState.Idle)
        val addState: StateFlow<AddPlaylistState> = mutableAddState.asStateFlow()

        fun addM3uUrl(
            name: String,
            url: String,
        ) = save { repository.addM3uUrl(name, url) }

        fun addM3uFile(
            name: String,
            uri: Uri,
        ) = save { repository.addM3uFile(name, uri) }

        fun addXtream(
            name: String,
            server: String,
            username: String,
            password: String,
        ) = save { repository.addXtream(name, server, username, password) }

        fun resetAddState() {
            mutableAddState.value = AddPlaylistState.Idle
        }

        fun refresh(id: Long) {
            viewModelScope.launch { repository.refresh(id) }
        }

        fun delete(id: Long) {
            viewModelScope.launch { repository.delete(id) }
        }

        private fun save(block: suspend () -> Unit) {
            if (mutableAddState.value == AddPlaylistState.Saving) return
            mutableAddState.value = AddPlaylistState.Saving
            viewModelScope.launch {
                mutableAddState.value =
                    try {
                        block()
                        AddPlaylistState.Done
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AddPlaylistState.Error(e.message ?: "Nu am putut adăuga playlist-ul.")
                    }
            }
        }
    }
