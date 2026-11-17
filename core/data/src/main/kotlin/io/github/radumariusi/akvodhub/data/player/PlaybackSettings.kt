package io.github.radumariusi.akvodhub.data.player

import android.content.Context
import io.github.radumariusi.akvodhub.parser.catalog.OriginalLanguage
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Cum arată subtitrările: culoarea textului și conturul / fundalul. */
enum class SubtitleStyle(
    val label: String,
) {
    WHITE_OUTLINE("Alb cu contur"),
    YELLOW_OUTLINE("Galben cu contur"),
    WHITE_BOX("Alb pe fundal negru"),
}

data class PlaybackPrefs(
    /** Profilul de alegere a sursei, ex. „4K EN, altfel 1080p RO”. */
    val profile: SourceProfile,
    /** Limbile subtitrărilor, în ordinea preferinței (ISO 639-1); goală = subtitrări oprite. */
    val subtitleLanguages: List<String>,
    /** Dacă sursa nu are subtitrare într-o limbă preferată, pornește una din addon-uri. */
    val addonSubtitlesAuto: Boolean = true,
    /** Mărimea subtitrărilor, în procente (100 = normal). */
    val subtitleScale: Int = 100,
    val subtitleStyle: SubtitleStyle = SubtitleStyle.WHITE_OUTLINE,
    val preferOriginalLanguage: Boolean = false,
    val posterBadges: Set<PosterBadge> = PosterBadges.defaults,
) {
    /** Prima limbă a subtitrărilor (pentru pistele încorporate), null = oprite. */
    val subtitleLanguage: String? get() = subtitleLanguages.firstOrNull()
}

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
    fun preferredLanguages(originalLanguage: String? = null): List<String> =
        (
            listOfNotNull(OriginalLanguage.code(originalLanguage).takeIf { state.value.preferOriginalLanguage }) +
                state.value.profile.languages + listOfNotNull(systemLanguage) + "en"
        ).filter { it.length == 2 }
            .distinct()

    fun togglePosterBadge(badge: PosterBadge) {
        val current = state.value.posterBadges
        val updated = if (badge in current) current - badge else current + badge
        prefs.edit().putString(BADGES, PosterBadges.encode(updated)).apply()
        mutableState.value = read()
    }

    fun setPreferOriginalLanguage(enabled: Boolean) {
        prefs.edit().putBoolean(ORIGINAL, enabled).apply()
        mutableState.value = read()
    }

    fun setProfile(profile: SourceProfile) {
        prefs.edit().putString(PROFILE, profile.encode()).apply()
        mutableState.value = read()
    }

    /** Limbile subtitrărilor în ordinea preferinței; listă goală = oprite. */
    fun setSubtitleLanguages(languages: List<String>) {
        val clean = languages.filter { it.length == 2 }.distinct().take(MAX_SUBTITLE_LANGUAGES)
        prefs.edit().putString(SUBTITLES, if (clean.isEmpty()) OFF else clean.joinToString(",")).apply()
        mutableState.value = read()
    }

    fun setAddonSubtitlesAuto(enabled: Boolean) {
        prefs.edit().putBoolean(ADDON_AUTO, enabled).apply()
        mutableState.value = read()
    }

    fun setSubtitleScale(percent: Int) {
        prefs.edit().putInt(SCALE, percent.coerceIn(MIN_SCALE, MAX_SCALE)).apply()
        mutableState.value = read()
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        prefs.edit().putString(STYLE, style.name).apply()
        mutableState.value = read()
    }

    /** Preferințele salvate, ca text (pentru backup). Cheile lipsă = valorile implicite. */
    fun backup(): Map<String, String> =
        buildMap {
            prefs.getString(PROFILE, null)?.let { put(PROFILE, it) }
            prefs.getString(BADGES, null)?.let { put(BADGES, it) }
            prefs.getString(SUBTITLES, null)?.let { put(SUBTITLES, it) }
            if (prefs.contains(ORIGINAL)) put(ORIGINAL, prefs.getBoolean(ORIGINAL, false).toString())
            if (prefs.contains(ADDON_AUTO)) put(ADDON_AUTO, prefs.getBoolean(ADDON_AUTO, true).toString())
            if (prefs.contains(SCALE)) put(SCALE, prefs.getInt(SCALE, 100).toString())
            prefs.getString(STYLE, null)?.let { put(STYLE, it) }
        }

    /** Restaurare din backup: doar cheile cunoscute, cu valori verificate la citire. */
    fun restore(values: Map<String, String>) {
        val edit = prefs.edit()
        values[PROFILE]?.let { edit.putString(PROFILE, it) }
        values[BADGES]?.let { edit.putString(BADGES, PosterBadges.encode(PosterBadges.decode(it))) }
        values[SUBTITLES]?.let { edit.putString(SUBTITLES, it) }
        values[ORIGINAL]?.toBooleanStrictOrNull()?.let { edit.putBoolean(ORIGINAL, it) }
        values[ADDON_AUTO]?.toBooleanStrictOrNull()?.let { edit.putBoolean(ADDON_AUTO, it) }
        values[SCALE]?.toIntOrNull()?.let { edit.putInt(SCALE, it.coerceIn(MIN_SCALE, MAX_SCALE)) }
        values[STYLE]?.let { edit.putString(STYLE, it) }
        edit.apply()
        mutableState.value = read()
    }

    private fun read(): PlaybackPrefs {
        // Până la etapa 7 se salva o singură limbă („ro”); acum o listă („ro,en”), compatibilă.
        val subtitles =
            when (val saved = prefs.getString(SUBTITLES, null)) {
                null -> {
                    listOfNotNull(
                        Locale
                            .getDefault()
                            .language
                            .lowercase()
                            .takeIf { it.length == 2 && it != "en" },
                    )
                }

                OFF -> {
                    emptyList()
                }

                else -> {
                    saved
                        .split(',')
                        .map { it.trim() }
                        .filter { it.length == 2 }
                        .distinct()
                }
            }
        return PlaybackPrefs(
            preferOriginalLanguage = prefs.getBoolean(ORIGINAL, false),
            posterBadges = PosterBadges.decode(prefs.getString(BADGES, null)),
            profile = SourceProfile.decode(prefs.getString(PROFILE, null)),
            subtitleLanguages = subtitles,
            addonSubtitlesAuto = prefs.getBoolean(ADDON_AUTO, true),
            subtitleScale = prefs.getInt(SCALE, 100).coerceIn(MIN_SCALE, MAX_SCALE),
            subtitleStyle =
                prefs.getString(STYLE, null)?.let { s -> SubtitleStyle.entries.firstOrNull { it.name == s } }
                    ?: SubtitleStyle.WHITE_OUTLINE,
        )
    }

    companion object {
        private const val BADGES = "poster_badges"
        private const val ORIGINAL = "prefer_original_language"
        private const val PROFILE = "profile"
        private const val SUBTITLES = "subtitles"
        private const val ADDON_AUTO = "subtitles_addon_auto"
        private const val SCALE = "subtitles_scale"
        private const val STYLE = "subtitles_style"
        private const val OFF = "off"
        const val MAX_SUBTITLE_LANGUAGES = 3
        const val MIN_SCALE = 50
        const val MAX_SCALE = 250
        val SCALES = listOf(75, 100, 125, 150, 175, 200)
    }
}
