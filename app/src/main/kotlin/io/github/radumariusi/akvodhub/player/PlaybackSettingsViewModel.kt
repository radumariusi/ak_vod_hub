package io.github.radumariusi.akvodhub.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.player.PlaybackPrefs
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import io.github.radumariusi.akvodhub.parser.player.SourceRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Setări → Redare: profilul de alegere a sursei și subtitrările automate. */
@HiltViewModel
class PlaybackSettingsViewModel
    @Inject
    constructor(
        private val settings: PlaybackSettings,
        private val repository: HubRepository,
    ) : ViewModel() {
        val prefs: StateFlow<PlaybackPrefs> = settings.state

        private val mutableLanguages = MutableStateFlow<List<String>>(emptyList())

        /** Limbile de ales: cele din playlist-uri (fără „MULTI”), plus RO și EN. */
        val languages: StateFlow<List<String>> = mutableLanguages.asStateFlow()

        val ffmpegStatus: String by lazy { PlayerFactory.ffmpegStatus() }

        init {
            loadLanguages()
        }

        fun loadLanguages() {
            viewModelScope.launch {
                val fromPlaylists = repository.languages().filter { it.length == 2 }
                mutableLanguages.value = (listOfNotNull(settings.systemLanguage, "en") + fromPlaylists).distinct()
            }
        }

        /** Schimbă regula [index] (null = o șterge); index = numărul de reguli adaugă una nouă. */
        fun setRule(
            index: Int,
            rule: SourceRule?,
        ) {
            val rules =
                prefs.value.profile.rules
                    .toMutableList()
            when {
                rule == null && index < rules.size -> rules.removeAt(index)
                rule != null && index < rules.size -> rules[index] = rule
                rule != null && rules.size < SourceProfile.MAX_RULES -> rules += rule
            }
            settings.setProfile(SourceProfile(rules))
        }

        fun setSubtitleLanguage(language: String?) = settings.setSubtitleLanguage(language)
    }
