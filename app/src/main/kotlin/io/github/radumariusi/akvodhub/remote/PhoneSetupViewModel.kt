package io.github.radumariusi.akvodhub.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.remote.PhoneSetupServer
import io.github.radumariusi.akvodhub.data.remote.SetupAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PhoneSetupState {
    data object Starting : PhoneSetupState

    data class Ready(
        val address: SetupAddress,
    ) : PhoneSetupState

    /** TV-ul nu are adresă în rețeaua locală (sau portul nu se poate deschide). */
    data object NoNetwork : PhoneSetupState
}

/** Ecranul „Configurare de pe telefon”: serverul local merge doar cât timp ecranul e deschis. */
@HiltViewModel
class PhoneSetupViewModel
    @Inject
    constructor(
        private val server: PhoneSetupServer,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<PhoneSetupState>(PhoneSetupState.Starting)
        val state: StateFlow<PhoneSetupState> = mutableState.asStateFlow()
        val events: StateFlow<List<String>> = server.events

        fun start() {
            viewModelScope.launch(Dispatchers.IO) {
                val address = runCatching { server.start(viewModelScope) }.getOrNull()
                mutableState.value = address?.let { PhoneSetupState.Ready(it) } ?: PhoneSetupState.NoNetwork
            }
        }

        fun stop() {
            server.stop()
            mutableState.value = PhoneSetupState.Starting
        }

        override fun onCleared() {
            server.stop()
        }
    }
