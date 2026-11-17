package io.github.radumariusi.akvodhub.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.player.PlaybackPrefs
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.player.SubtitleStyle
import io.github.radumariusi.akvodhub.data.stremio.StremioAddon
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.data.stremio.StremioClient
import io.github.radumariusi.akvodhub.data.stremio.SubtitleRepository
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import io.github.radumariusi.akvodhub.parser.player.SourceRule
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Adăugarea unui addon Stremio după adresă. */
sealed interface AddonAddState {
    data object Idle : AddonAddState

    data object Checking : AddonAddState

    data class Added(
        val name: String,
    ) : AddonAddState

    data class Error(
        val message: String,
    ) : AddonAddState
}

/** Setări → Redare și Subtitrări: profilul de alegere a sursei, limbile subtitrărilor, addon-urile Stremio. */
@HiltViewModel
class PlaybackSettingsViewModel
    @Inject
    constructor(
        private val settings: PlaybackSettings,
        private val repository: HubRepository,
        private val stremioAddons: StremioAddons,
        private val stremio: StremioClient,
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

        /** Limbile de ales pentru subtitrări: limba sistemului, apoi cele mai folosite. */
        val subtitleLanguageOptions: List<String> =
            (listOfNotNull(settings.systemLanguage) + COMMON_SUBTITLE_LANGUAGES).distinct()

        val addons: StateFlow<List<StremioAddon>> = stremioAddons.addons

        private val mutableAddState = MutableStateFlow<AddonAddState>(AddonAddState.Idle)
        val addState: StateFlow<AddonAddState> = mutableAddState.asStateFlow()

        /** Schimbă limba [index] a subtitrărilor (null = o scoate); index = numărul de limbi adaugă una. */
        fun setSubtitleLanguage(
            index: Int,
            language: String?,
        ) {
            val list = prefs.value.subtitleLanguages.toMutableList()
            when {
                language == null && index < list.size -> list.removeAt(index)
                language != null && index < list.size -> list[index] = language
                language != null -> list += language
            }
            settings.setSubtitleLanguages(list.distinct())
        }

        fun setAddonSubtitlesAuto(enabled: Boolean) = settings.setAddonSubtitlesAuto(enabled)

        fun setSubtitleScale(percent: Int) = settings.setSubtitleScale(percent)

        fun setSubtitleStyle(style: SubtitleStyle) = settings.setSubtitleStyle(style)

        /** Verifică manifestul și adaugă addon-ul (trebuie să dea subtitrări sau cataloage). */
        fun addAddon(input: String) {
            val url = StremioIds.normalizeManifestUrl(input)
            if (url == null) {
                mutableAddState.value =
                    AddonAddState.Error("Adresa nu pare corectă. Exemplu: https://opensubtitles-v3.strem.io")
                return
            }
            mutableAddState.value = AddonAddState.Checking
            viewModelScope.launch {
                mutableAddState.value =
                    try {
                        val manifest = stremio.manifest(url)
                        val resources = manifest.resources.map { it.name }.distinct()
                        if ("subtitles" !in resources && "catalog" !in resources) {
                            AddonAddState.Error("„${manifest.name}” nu oferă subtitrări sau cataloage.")
                        } else {
                            stremioAddons.add(StremioAddon(url, manifest.name, resources))
                            AddonAddState.Added(manifest.name)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AddonAddState.Error("Addon-ul nu se poate citi: ${SubtitleRepository.reason(e)}.")
                    }
            }
        }

        fun addOpenSubtitles() = stremioAddons.add(StremioAddons.OPENSUBTITLES)

        fun removeAddon(manifestUrl: String) = stremioAddons.remove(manifestUrl)

        fun moveAddonUp(manifestUrl: String) = stremioAddons.moveUp(manifestUrl)

        fun resetAddState() {
            mutableAddState.value = AddonAddState.Idle
        }

        private companion object {
            val COMMON_SUBTITLE_LANGUAGES =
                listOf(
                    "ro",
                    "en",
                    "fr",
                    "de",
                    "es",
                    "it",
                    "pt",
                    "hu",
                    "bg",
                    "ru",
                    "uk",
                    "el",
                    "tr",
                    "pl",
                    "cs",
                    "sk",
                    "sr",
                    "hr",
                    "nl",
                    "sv",
                    "da",
                    "no",
                    "fi",
                    "he",
                    "ar",
                    "zh",
                    "ja",
                    "ko",
                )
        }
    }
