package io.github.radumariusi.akvodhub.data.player

import android.content.Context
import io.github.radumariusi.akvodhub.parser.catalog.AccentColor
import io.github.radumariusi.akvodhub.parser.catalog.BrowseFilters
import io.github.radumariusi.akvodhub.parser.catalog.HomeRows
import io.github.radumariusi.akvodhub.parser.catalog.OriginalLanguage
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.catalog.PosterSize
import io.github.radumariusi.akvodhub.parser.player.BufferPreset
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
    val homeHero: Boolean = true,
    val homeOrder: String = "",
    val homeHidden: Set<String> = emptySet(),
    val continueDirect: Boolean = false,
    val hideAdultMovies: Boolean = false,
    val startScreen: String = "HOME",
    val controlsSeconds: Int = 5,
    val showPlaylistStatistics: Boolean = false,
    val defaultSpeed: Float = 1f,
    val autoNext: Boolean = true,
    val audioLanguage: String = "",
    val subtitleDelayMs: Long = 0L,
    val contentLanguages: String = "",
    val hiddenLanguages: String = "",
    val hiddenCategories: String = "",
    val preferOriginalLanguage: Boolean = false,
    val matchFrameRate: Boolean = false,
    val audioPassthrough: Boolean = false,
    val nightMode: Boolean = false,
    val bufferPreset: BufferPreset = BufferPreset.NORMAL,
    val posterBadges: Set<PosterBadge> = PosterBadges.defaults,
    val posterSize: PosterSize = PosterSize.MEDIUM,
    val accentColor: AccentColor = AccentColor.RED,
    val browseFilters: Map<String, String> = emptyMap(),
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
                io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
                    .tokens(state.value.contentLanguages) +
                state.value.profile.languages + listOfNotNull(systemLanguage) + "en"
        ).filter { it.length == 2 }
            .distinct()

    fun audioLanguages(originalLanguage: String?): List<String> =
        (
            listOfNotNull(OriginalLanguage.code(originalLanguage).takeIf { state.value.preferOriginalLanguage }) +
                listOf(state.value.audioLanguage).filter { it.length == 2 } + preferredLanguages(originalLanguage)
        ).distinct()

    fun seriesTrack(key: String, type: String): String? = prefs.getString("series.$key.$type", null)

    fun setSeriesTrack(key: String, type: String, value: String) {
        val name = "series.$key.$type"
        if (io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
                .validSetting(name, value)
        ) {
            prefs.edit().putString(name, value).apply()
        }
    }

    fun clearSeriesSubtitle(key: String) {
        prefs.edit().remove("series.$key.text").apply()
    }

    fun clearSeriesTracks(key: String) {
        prefs
            .edit()
            .remove("series.$key.audio")
            .remove("series.$key.text")
            .apply()
    }

    fun seriesIntro(key: String): Long = prefs.getString("series.$key.intro", null)?.toLongOrNull() ?: 0L

    fun setSeriesIntro(key: String, endMs: Long) {
        val name = "series.$key.intro"
        if (endMs == 0L) {
            prefs.edit().remove(name).apply()
        } else if (io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
                .validSetting(name, endMs.toString())
        ) {
            prefs.edit().putString(name, endMs.toString()).apply()
        }
    }

    fun setBrowseFilter(key: String, value: String) {
        val safe = BrowseFilters.sanitize(key, value) ?: return
        prefs.edit().putString(key, safe).apply()
        mutableState.value = read()
    }

    fun setHomeRows(order: List<String>, hidden: Set<String>) {
        prefs
            .edit()
            .putString("home_order", HomeRows.keys(order.joinToString(",")).joinToString(","))
            .putString("home_hidden", HomeRows.keys(hidden.joinToString(",")).joinToString(","))
            .apply()
        mutableState.value = read()
    }

    fun setHideAdultMovies(value: Boolean) = setFlag("hide_adult_movies", value)

    fun setShowPlaylistStatistics(value: Boolean) = setFlag("show_playlist_statistics", value)

    private fun setFlag(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        mutableState.value = read()
    }

    fun setStartScreen(value: String) {
        if (value !in START_SCREENS) return
        prefs.edit().putString("start_screen", value).apply()
        mutableState.value = read()
    }

    fun setControlsSeconds(value: Int) {
        if (value !in CONTROLS_SECONDS) return
        prefs.edit().putInt("controls_seconds", value).apply()
        mutableState.value = read()
    }

    fun resetCategory(category: Int) {
        val keys =
            when (category) {
                0 -> {
                    listOf("content_languages", "hidden_languages", "hidden_categories", "hide_adult_movies", ORIGINAL)
                }

                1 -> {
                    listOf(POSTER_SIZE, ACCENT, BADGES, "home_hero", "home_order", "home_hidden")
                }

                2 -> {
                    listOf(
                        PROFILE,
                        BUFFER,
                        FRAME_RATE,
                        "default_speed",
                        "auto_next",
                        "controls_seconds",
                        "continue_direct",
                    )
                }

                3 -> {
                    listOf(NIGHT, PASSTHROUGH, "audio_language")
                }

                4 -> {
                    listOf(SUBTITLES, ADDON_AUTO, SCALE, STYLE, "subtitle_delay_ms")
                }

                5 -> {
                    listOf("show_playlist_statistics")
                }

                7 -> {
                    listOf("start_screen")
                }

                else -> {
                    emptyList()
                }
            }
        val edit = prefs.edit()
        keys.forEach(edit::remove)
        edit.apply()
        mutableState.value = read()
    }

    fun setContinueDirect(value: Boolean) {
        prefs.edit().putBoolean("continue_direct", value).apply()
        mutableState.value = read()
    }

    fun setHomeHero(value: Boolean) {
        prefs.edit().putBoolean("home_hero", value).apply()
        mutableState.value = read()
    }

    fun setDefaultSpeed(value: Float) {
        prefs
            .edit()
            .putFloat(
                "default_speed",
                io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults
                    .speed(value),
            ).apply()
        mutableState.value = read()
    }

    fun setAutoNext(value: Boolean) {
        prefs.edit().putBoolean("auto_next", value).apply()
        mutableState.value = read()
    }

    fun setSubtitleDelayMs(value: Long) {
        prefs
            .edit()
            .putLong(
                "subtitle_delay_ms",
                io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults
                    .offset(value),
            ).apply()
        mutableState.value = read()
    }

    fun setContent(key: String, value: String) {
        if (key !in CONTENT_KEYS) return
        val clean =
            io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
                .tokens(value, lowercase = key != "hidden_categories")
                .joinToString(",")
        prefs.edit().putString(key, clean).apply()
        mutableState.value = read()
    }

    fun setBufferPreset(value: BufferPreset) {
        prefs.edit().putString(BUFFER, value.name).apply()
        mutableState.value = read()
    }

    fun setNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(NIGHT, enabled).apply()
        mutableState.value = read()
    }

    fun setAudioPassthrough(enabled: Boolean) {
        prefs.edit().putBoolean(PASSTHROUGH, enabled).apply()
        mutableState.value = read()
    }

    fun setMatchFrameRate(enabled: Boolean) {
        prefs.edit().putBoolean(FRAME_RATE, enabled).apply()
        mutableState.value = read()
    }

    fun setPosterSize(size: PosterSize) {
        prefs.edit().putString(POSTER_SIZE, size.name).apply()
        mutableState.value = read()
    }

    fun setAccentColor(color: AccentColor) {
        prefs.edit().putString(ACCENT, color.name).apply()
        mutableState.value = read()
    }

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
            prefs.all.forEach { (key, value) ->
                if (value is String &&
                    io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
                        .validSetting(key, value)
                ) {
                    put(key, value)
                }
            }
            putAll(state.value.browseFilters)
            put("home_order", state.value.homeOrder)
            put("home_hidden", state.value.homeHidden.joinToString(","))
            put("continue_direct", state.value.continueDirect.toString())
            put("hide_adult_movies", state.value.hideAdultMovies.toString())
            put("start_screen", state.value.startScreen)
            put("controls_seconds", state.value.controlsSeconds.toString())
            put("show_playlist_statistics", state.value.showPlaylistStatistics.toString())
            put("home_hero", state.value.homeHero.toString())
            put("default_speed", state.value.defaultSpeed.toString())
            put("auto_next", state.value.autoNext.toString())
            put("subtitle_delay_ms", state.value.subtitleDelayMs.toString())
            CONTENT_KEYS.forEach { key -> prefs.getString(key, null)?.let { put(key, it) } }
            put(BUFFER, state.value.bufferPreset.name)
            put(NIGHT, state.value.nightMode.toString())
            put(PASSTHROUGH, state.value.audioPassthrough.toString())
            put(FRAME_RATE, state.value.matchFrameRate.toString())
            prefs.getString(PROFILE, null)?.let { put(PROFILE, it) }
            prefs.getString(BADGES, null)?.let { put(BADGES, it) }
            prefs.getString(POSTER_SIZE, null)?.let { put(POSTER_SIZE, it) }
            prefs.getString(ACCENT, null)?.let { put(ACCENT, it) }
            prefs.getString(SUBTITLES, null)?.let { put(SUBTITLES, it) }
            if (prefs.contains(ORIGINAL)) put(ORIGINAL, prefs.getBoolean(ORIGINAL, false).toString())
            if (prefs.contains(ADDON_AUTO)) put(ADDON_AUTO, prefs.getBoolean(ADDON_AUTO, true).toString())
            if (prefs.contains(SCALE)) put(SCALE, prefs.getInt(SCALE, 100).toString())
            prefs.getString(STYLE, null)?.let { put(STYLE, it) }
        }

    /** Restaurare din backup: doar cheile cunoscute, cu valori verificate la citire. */
    fun restore(values: Map<String, String>) {
        values["subtitle_delay_ms"]?.toLongOrNull()?.let { setSubtitleDelayMs(it) }
        values["auto_next"]?.toBooleanStrictOrNull()?.let { setAutoNext(it) }
        values["default_speed"]?.toFloatOrNull()?.let { setDefaultSpeed(it) }
        values["home_hero"]?.toBooleanStrictOrNull()?.let { setHomeHero(it) }
        CONTENT_KEYS.forEach { key -> values[key]?.let { setContent(key, it) } }
        val edit = prefs.edit()
        for (key in listOf("home_order", "home_hidden")) {
            values[key]?.let { edit.putString(key, HomeRows.keys(it.take(1500)).joinToString(",")) }
        }
        for (key in listOf("continue_direct", "hide_adult_movies", "show_playlist_statistics")) {
            values[key]?.toBooleanStrictOrNull()?.let { edit.putBoolean(key, it) }
        }
        values["start_screen"]?.takeIf { it in START_SCREENS }?.let { edit.putString("start_screen", it) }
        values["controls_seconds"]
            ?.toIntOrNull()
            ?.takeIf {
                it in CONTROLS_SECONDS
            }?.let { edit.putInt("controls_seconds", it) }
        BrowseFilters.keys.forEach { key ->
            values[key]?.let { BrowseFilters.sanitize(key, it) }?.let { edit.putString(key, it) }
        }
        values[BUFFER]?.let { edit.putString(BUFFER, BufferPreset.decode(it).name) }
        values.forEach { (key, value) ->
            if (io.github.radumariusi.akvodhub.parser.player.SeriesPlayback.validSetting(
                    key,
                    value,
                )
            ) {
                edit.putString(key, value)
            }
        }
        values[NIGHT]?.toBooleanStrictOrNull()?.let { edit.putBoolean(NIGHT, it) }
        values[PASSTHROUGH]?.toBooleanStrictOrNull()?.let { edit.putBoolean(PASSTHROUGH, it) }
        values[FRAME_RATE]?.toBooleanStrictOrNull()?.let { edit.putBoolean(FRAME_RATE, it) }
        values[POSTER_SIZE]?.let { edit.putString(POSTER_SIZE, PosterSize.decode(it).name) }
        values[ACCENT]?.let { edit.putString(ACCENT, AccentColor.decode(it).name) }
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
            browseFilters =
                BrowseFilters.keys
                    .mapNotNull { key ->
                        prefs.getString(key, null)?.let { key to it }
                    }.toMap(),
            homeOrder = prefs.getString("home_order", "").orEmpty(),
            homeHidden = HomeRows.keys(prefs.getString("home_hidden", "").orEmpty()).toSet(),
            continueDirect = prefs.getBoolean("continue_direct", false),
            hideAdultMovies = prefs.getBoolean("hide_adult_movies", false),
            startScreen = prefs.getString("start_screen", "HOME").takeIf { it in START_SCREENS } ?: "HOME",
            controlsSeconds = prefs.getInt("controls_seconds", 5).takeIf { it in CONTROLS_SECONDS } ?: 5,
            showPlaylistStatistics = prefs.getBoolean("show_playlist_statistics", false),
            homeHero = prefs.getBoolean("home_hero", true),
            defaultSpeed =
                io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults.speed(
                    prefs.getFloat("default_speed", 1f),
                ),
            autoNext = prefs.getBoolean("auto_next", true),
            audioLanguage = prefs.getString("audio_language", "").orEmpty(),
            subtitleDelayMs =
                io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults.offset(
                    prefs.getLong("subtitle_delay_ms", 0L),
                ),
            contentLanguages = prefs.getString("content_languages", "").orEmpty(),
            hiddenLanguages = prefs.getString("hidden_languages", "").orEmpty(),
            hiddenCategories = prefs.getString("hidden_categories", "").orEmpty(),
            bufferPreset = BufferPreset.decode(prefs.getString(BUFFER, null)),
            nightMode = prefs.getBoolean(NIGHT, false),
            audioPassthrough = prefs.getBoolean(PASSTHROUGH, false),
            matchFrameRate = prefs.getBoolean(FRAME_RATE, false),
            preferOriginalLanguage = prefs.getBoolean(ORIGINAL, false),
            posterBadges = PosterBadges.decode(prefs.getString(BADGES, null)),
            posterSize = PosterSize.decode(prefs.getString(POSTER_SIZE, null)),
            accentColor = AccentColor.decode(prefs.getString(ACCENT, null)),
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
        val START_SCREENS = listOf("HOME", "MOVIES", "SERIES", "WATCHLIST", "SEARCH", "SETTINGS")
        val CONTROLS_SECONDS = listOf(3, 5, 8, 12, 20)
        private val CONTENT_KEYS =
            listOf("content_languages", "hidden_languages", "hidden_categories", "audio_language")
        private const val BUFFER = "buffer_preset"
        private const val NIGHT = "night_mode"
        private const val PASSTHROUGH = "audio_passthrough"
        private const val FRAME_RATE = "match_frame_rate"
        private const val POSTER_SIZE = "poster_size"
        private const val ACCENT = "accent_color"
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
