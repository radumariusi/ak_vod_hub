package io.github.radumariusi.akvodhub.data.player

import android.content.Context
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class PlaybackPrefs(
    /** Profilul de alegere a sursei, ex. „4K EN, altfel 1080p RO”. */
    val profile: SourceProfile,
    /** Limba subtitrării pornite automat (ISO 639-1); null = subtitrări oprite. */
    val subtitleLanguage: String?,
)

/** Preferințele de redare (doar pe dispozitiv). */
class PlaybackSettings(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<PlaybackPrefs> = mutableState.asStateFlow()

    /** Limba sistemului (ex. `ro`), dacă are cod din două litere. */
    val systemLanguage: String? =
        Locale
            .getDefault()
            .language
            .lowercase()
            .takeIf { it.length == 2 }

    /** Limbile preferate pentru ordonarea surselor și pista audio: profilul, apoi limba sistemului, apoi engleza. */
    fun preferredLanguages(): List<String> =
        (state.value.profile.languages + listOfNotNull(systemLanguage) + "en")
            .filter { it.length == 2 }
            .distinct()

    fun setProfile(profile: SourceProfile) {
        prefs.edit().putString(PROFILE, profile.encode()).apply()
        mutableState.value = read()
    }

    fun setSubtitleLanguage(language: String?) {
        prefs.edit().putString(SUBTITLES, language ?: OFF).apply()
        mutableState.value = read()
    }

    private fun read(): PlaybackPrefs {
        val subtitles =
            when (val saved = prefs.getString(SUBTITLES, null)) {
                null -> {
                    Locale
                        .getDefault()
                        .language
                        .lowercase()
                        .takeIf { it.length == 2 && it != "en" }
                }

                OFF -> {
                    null
                }

                else -> {
                    saved
                }
            }
        return PlaybackPrefs(SourceProfile.decode(prefs.getString(PROFILE, null)), subtitles)
    }

    private companion object {
        const val PROFILE = "profile"
        const val SUBTITLES = "subtitles"
        const val OFF = "off"
    }
}
