package io.github.radumariusi.akvodhub.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.DiagnosticCategory
import io.github.radumariusi.akvodhub.data.diagnostic.SourceDiagnostics
import io.github.radumariusi.akvodhub.data.player.PlaybackPrefs
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.player.SubtitleStyle
import io.github.radumariusi.akvodhub.data.stremio.AddonInstaller
import io.github.radumariusi.akvodhub.data.stremio.StremioAddon
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.parser.catalog.AccentColor
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.parser.catalog.PosterSize
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import io.github.radumariusi.akvodhub.parser.player.SourceRule
import io.github.radumariusi.akvodhub.parser.title.CategoryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        private val installer: AddonInstaller,
        private val diagnostics: SourceDiagnostics,
    ) : ViewModel() {
        private val mutableDiagnostic = MutableStateFlow<String?>(null)

        /** Rezumatul „Diagnostic surse” din Setări; null = necalculat. */
        val diagnostic = mutableDiagnostic.asStateFlow()

        fun loadDiagnostic() {
            mutableDiagnostic.value = "Se calculează…"
            viewModelScope.launch {
                mutableDiagnostic.value =
                    runCatching { diagnostics.summary() }
                        .map { s ->
                            buildString {
                                append("${s.total} surse · ${s.noLanguage} fără limbă audio · ")
                                append("${s.noQuality} fără calitate declarată.")
                                if (s.categoriesWithoutLanguage.isNotEmpty()) {
                                    append("\nCategorii mari fără limbă: ")
                                    append(
                                        s.categoriesWithoutLanguage
                                            .take(
                                                8,
                                            ).joinToString(", ") { "${it.first} (${it.second})" },
                                    )
                                }
                            }
                        }.getOrElse { "Diagnosticul nu a putut fi calculat." }
            }
        }

        val prefs: StateFlow<PlaybackPrefs> = settings.state
        private val mutableStatistics = MutableStateFlow("Se calculează…")
        val statistics = mutableStatistics.asStateFlow()

        fun loadStatistics() {
            viewModelScope.launch {
                mutableStatistics.value = repository.statistics()
            }
        }

        private val mutableLanguages = MutableStateFlow<List<String>>(emptyList())

        /** Limbile de ales: cele din playlist-uri (fără „MULTI”), plus RO și EN. */
        val languages: StateFlow<List<String>> = mutableLanguages.asStateFlow()

        val ffmpegStatus: String by lazy { PlayerFactory.ffmpegStatus() }

        init {
            loadLanguages()
            viewModelScope.launch {
                prefs
                    .map {
                        Triple(
                            it.hiddenLanguages,
                            it.hiddenCategories,
                            it.hideAdultMovies,
                        )
                    }.distinctUntilChanged()
                    .collect {
                        repository.prepare()
                    }
            }
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

        private val mutableCategories = MutableStateFlow<List<DiagnosticCategory>?>(null)

        /** Categoriile din playlist (null = încă neîncărcate). */
        val categories = mutableCategories.asStateFlow()
        private val mutableCategoryRules = MutableStateFlow(repository.categoryRules())
        val categoryRules = mutableCategoryRules.asStateFlow()

        fun loadCategories() {
            viewModelScope.launch {
                mutableCategories.value = runCatching { diagnostics.categories() }.getOrDefault(emptyList())
            }
        }

        /** Automat → Audio original → Dublat → Automat. */
        fun cycleCategoryRule(category: String) {
            val key = category.trim()
            val current = mutableCategoryRules.value[key] ?: CategoryMode.AUTO
            val next = CategoryMode.entries[(current.ordinal + 1) % CategoryMode.entries.size]
            val rules = mutableCategoryRules.value + (key to next)
            mutableCategoryRules.value = rules.filterValues { it != CategoryMode.AUTO }
            repository.setCategoryRules(rules)
        }

        fun setContent(key: String, value: String) {
            settings.setContent(key, value)
        }

        fun setAudioPassthrough(value: Boolean) = settings.setAudioPassthrough(value)

        fun setNightMode(value: Boolean) = settings.setNightMode(value)

        fun setDefaultSpeed(value: Float) = settings.setDefaultSpeed(value)

        fun setAutoNext(value: Boolean) = settings.setAutoNext(value)

        fun setMatchFrameRate(value: Boolean) = settings.setMatchFrameRate(value)

        fun setBufferPreset(value: io.github.radumariusi.akvodhub.parser.player.BufferPreset) =
            settings
                .setBufferPreset(
                    value,
                )

        fun setHideAdultMovies(value: Boolean) = settings.setHideAdultMovies(value)

        fun setShowPlaylistStatistics(value: Boolean) = settings.setShowPlaylistStatistics(value)

        fun setStartScreen(value: String) = settings.setStartScreen(value)

        fun setControlsSeconds(value: Int) = settings.setControlsSeconds(value)

        fun resetCategory(category: Int) = settings.resetCategory(category)

        fun setHomeRows(order: List<String>, hidden: Set<String>) = settings.setHomeRows(order, hidden)

        fun setAddonHomeRowsVisible(visible: Boolean) = settings.setAddonHomeRowsVisible(visible)

        fun setContinueDirect(value: Boolean) = settings.setContinueDirect(value)

        fun setHomeHero(enabled: Boolean) = settings.setHomeHero(enabled)

        fun setPosterSize(size: PosterSize) = settings.setPosterSize(size)

        fun setAccentColor(color: AccentColor) = settings.setAccentColor(color)

        fun togglePosterBadge(badge: PosterBadge) = settings.togglePosterBadge(badge)

        fun setPreferOriginalLanguage(enabled: Boolean) = settings.setPreferOriginalLanguage(enabled)

        fun setAddonSubtitlesAuto(enabled: Boolean) = settings.setAddonSubtitlesAuto(enabled)

        fun setSubtitleDelayMs(value: Long) = settings.setSubtitleDelayMs(value)

        fun setSubtitleScale(percent: Int) = settings.setSubtitleScale(percent)

        fun setSubtitleStyle(style: SubtitleStyle) = settings.setSubtitleStyle(style)

        /** Verifică manifestul și adaugă addon-ul (trebuie să dea subtitrări sau cataloage). */
        fun addAddon(input: String) {
            mutableAddState.value = AddonAddState.Checking
            viewModelScope.launch {
                mutableAddState.value =
                    try {
                        AddonAddState.Added(installer.install(input).name)
                    } catch (e: IllegalArgumentException) {
                        AddonAddState.Error(e.message ?: "Addon-ul nu se poate adăuga.")
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
