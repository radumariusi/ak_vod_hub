package io.github.radumariusi.akvodhub.player

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.catalog.SourceDetails
import io.github.radumariusi.akvodhub.data.db.EpisodeProgressEntity
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.db.WatchStateEntity
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.data.player.PlaybackPrefs
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.player.SubtitleStyle
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.data.stremio.SubtitleRepository
import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.catalog.MergedSeason
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.player.BufferPreset
import io.github.radumariusi.akvodhub.parser.player.EpisodeOrder
import io.github.radumariusi.akvodhub.parser.player.FallbackQueue
import io.github.radumariusi.akvodhub.parser.player.PlaybackRecovery
import io.github.radumariusi.akvodhub.parser.player.RebufferHistory
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.parser.player.SeekNavigation
import io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
import io.github.radumariusi.akvodhub.parser.player.SleepTimer
import io.github.radumariusi.akvodhub.parser.stremio.AddonSubtitle
import io.github.radumariusi.akvodhub.parser.stremio.SubtitleChoice
import io.github.radumariusi.akvodhub.parser.stremio.SubtitleLanguages
import io.github.radumariusi.akvodhub.parser.subtitle.SubtitleDocument
import io.github.radumariusi.akvodhub.parser.title.VideoResolution
import io.github.radumariusi.akvodhub.ui.hub.sourceLabel
import io.github.radumariusi.akvodhub.ui.hub.sourceShortLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce trebuie redat: titlul, opțional sursa aleasă, episodul (la seriale) și dacă reluăm de unde ai rămas. */
data class PlayRequest(
    val titleId: Long,
    val entryId: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val resume: Boolean = false,
    val openTrackMenu: Boolean = false,
)

/** O sursă în meniul „Sursă” al playerului. */
data class SourceOption(
    val entryId: Long,
    val label: String,
    val failedReason: String?,
    val current: Boolean,
)

/** O subtitrare din addon-uri în meniul „Subtitrări”, ex. „Română 2 · OpenSubtitles v3”. */
data class AddonSubtitleOption(
    val subtitle: AddonSubtitle,
    val label: String,
)

data class PlayerUiState(
    val sleepLabel: String = "Oprit",
    val isSeries: Boolean = false,
    val introEndMs: Long = 0,
    val pictureMode: Int = 0,
    val speed: Float = 1f,
    val title: String = "",
    /** La seriale: „S01E03 · Titlul episodului”. */
    val subtitle: String? = null,
    /** Sursa curentă, ex. „Sursa 1 din 5 · 4K · EN · H265 · MKV — Playlist A”. */
    val sourceLabel: String? = null,
    val detectedResolution: String? = null,
    val loading: Boolean = true,
    /** Mesaj trecător, ex. „4K EN nu merge (HTTP 404). Încerc sursa 2 din 5: 1080p RO”. */
    val notice: String? = null,
    /** Nicio sursă nu a pornit (sau titlul lipsește). */
    val error: String? = null,
    val failures: List<String> = emptyList(),
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList(),
    val subtitlesOff: Boolean = false,
    /** Subtitrările găsite în addon-urile Stremio (limbile preferate primele). */
    val addonSubtitles: List<AddonSubtitleOption> = emptyList(),
    /** Starea căutării în addon-uri (ex. „Caut subtitrări…”); null când lista e gata și nu e nimic de spus. */
    val addonStatus: String? = null,
    /** Subtitrarea din addon afișată acum (adresa ei), null = niciuna. */
    val externalSubtitle: String? = null,
    val externalLabel: String? = null,
    /** Decalajul subtitrării din addon: pozitiv = apare mai târziu. */
    val subtitleOffsetMs: Long = 0,
    val sources: List<SourceOption> = emptyList(),
    /** Eticheta episodului următor, dacă există. */
    val next: String? = null,
    /** Secundele rămase până pornește singur episodul următor. */
    val countdown: Int? = null,
    /** Redarea s-a încheiat: ecranul se închide. */
    val finished: Boolean = false,
)

/**
 * Playerul: pornește sursa cea mai potrivită (profilul din Setări → Redare) și, dacă nu merge (eroare,
 * nu pornește în [START_TIMEOUT_MS], se blochează, codec nesuportat), trece singur la următoarea sursă de la
 * aceeași poziție. Salvează progresul pentru „Continuă vizionarea” și pornește automat episodul următor.
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val repository: HubRepository,
        private val settings: PlaybackSettings,
        private val subtitleRepository: SubtitleRepository,
        private val addons: StremioAddons,
    ) : ViewModel() {
        private val passthroughRequested = settings.state.value.audioPassthrough && !settings.state.value.nightMode
        private val nightModeEnabled = settings.state.value.nightMode
        val player: ExoPlayer =
            PlayerFactory.create(
                context,
                trackPreferences(),
                settings.state.value.audioPassthrough,
                settings.state.value.nightMode,
                settings.state.value.bufferPreset,
            )

        /** Mărimea și stilul subtitrărilor (Setări → Subtitrări; se pot schimba și din player). */
        val prefs: StateFlow<PlaybackPrefs> = settings.state

        private val mutableDocument = MutableStateFlow<SubtitleDocument?>(null)

        /** Subtitrarea din addon afișată peste video (null = folosim pistele sursei). */
        val externalDocument: StateFlow<SubtitleDocument?> = mutableDocument.asStateFlow()

        private val mutableState = MutableStateFlow(PlayerUiState(speed = settings.state.value.defaultSpeed))

        init {
            player.setPlaybackSpeed(settings.state.value.defaultSpeed)
        }

        val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

        /** Scrierile progresului nu se anulează când se închide ecranul. */
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private var request: PlayRequest? = null
        private var title: TitleEntity? = null
        private var seasons: List<MergedSeason> = emptyList()
        private var details: Map<Long, SourceDetails> = emptyMap()
        private var sources: Map<Long, SourceInfo> = emptyMap()
        private var episode: MergedEpisode? = null
        private var queue: FallbackQueue? = null

        /** Sursa curentă a ajuns măcar o dată „gata de redare”. */
        private var started = false
        private var retriedSame = false
        private var startPosition = 0L
        private var generation = 0
        private var watchdog: Job? = null
        private var rebufferHistory = RebufferHistory()
        private var adaptiveSteps = 0
        private var countdownJob: Job? = null
        private var sleepJob: Job? = null
        private var sleepTimer = SleepTimer()
        private var noticeJob: Job? = null

        private var addonJob: Job? = null
        private var addonLoadJob: Job? = null

        /** Subtitrările din addon-uri pentru titlul / episodul curent (null = încă se caută). */
        private var addonResults: List<AddonSubtitle>? = null

        /** Limbile subtitrărilor încorporate în sursa curentă (null = pistele nu se știu încă). */
        private var embeddedTextLanguages: List<String?>? = null

        /** Alegerea automată a subtitrării s-a făcut (sau utilizatorul a ales singur) pentru acest titlu / episod. */
        private var subtitleDecided = false

        private val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player.currentMediaItem?.mediaId != queue?.current?.toString()) return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            watchdog?.cancel()
                            mutableState.update { it.copy(loading = false) }
                            if (!started) {
                                started = true
                            } else if (player.playWhenReady && rebufferHistory.ready(SystemClock.elapsedRealtime())) {
                                recoverUnstableSource("întreruperi repetate")
                            }
                        }

                        Player.STATE_BUFFERING -> {
                            mutableState.update { it.copy(loading = true) }
                            if (started && player.playWhenReady) {
                                rebufferHistory.buffering(SystemClock.elapsedRealtime())
                                startStallWatchdog()
                            }
                        }

                        Player.STATE_ENDED -> {
                            onEnded()
                        }

                        else -> {}
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady) {
                        watchdog?.cancel()
                        rebufferHistory.reset()
                    } else if (player.playbackState == Player.STATE_BUFFERING) {
                        if (started) {
                            rebufferHistory.buffering(SystemClock.elapsedRealtime())
                            startStallWatchdog()
                        } else {
                            startWatchdog(START_TIMEOUT_MS, "bufferul nu se umple")
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                    ) {
                        rebufferHistory.reset(ignoreBefore = SystemClock.elapsedRealtime() + 5_000L)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player.currentMediaItem?.mediaId != queue?.current?.toString()) return
                    if (started && !retriedSame && PlaybackErrors.isNetwork(error)) {
                        // Mergea: întâi o reîncercare a aceleiași surse (o întrerupere scurtă a rețelei).
                        retriedSame = true
                        showNotice("Conexiunea s-a întrerupt, reiau sursa…")
                        player.prepare()
                        startStallWatchdog()
                        return
                    }
                    sourceFailed(PlaybackErrors.describe(error))
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val entryId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                    if (entryId != queue?.current) return
                    val quality = VideoResolution.quality(videoSize.width, videoSize.height) ?: return
                    mutableState.update { it.copy(detectedResolution = "${videoSize.width} × ${videoSize.height}") }
                    viewModelScope.launch { repository.confirmQuality(entryId, quality) }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (player.currentMediaItem?.mediaId != queue?.current?.toString()) return
                    if (tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)) {
                        val codec = TrackOptions.videoCodec(tracks)?.let { "$it " }.orEmpty()
                        sourceFailed("video ${codec}nesuportat de acest TV")
                        return
                    }
                    if (tracks.containsType(C.TRACK_TYPE_AUDIO) &&
                        !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) &&
                        queue?.hasMore == true
                    ) {
                        sourceFailed("audio nesuportat de acest TV")
                        return
                    }
                    mutableState.update {
                        it.copy(
                            audio = TrackOptions.list(tracks, C.TRACK_TYPE_AUDIO),
                            subtitles = TrackOptions.list(tracks, C.TRACK_TYPE_TEXT),
                        )
                    }
                    if (!tracks.isEmpty) {
                        embeddedTextLanguages =
                            TrackOptions.languages(tracks, C.TRACK_TYPE_TEXT).map(SubtitleLanguages::toIso1)
                        autoPickSubtitle()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) save()
                }
            }

        init {
            player.addListener(listener)
            viewModelScope.launch {
                while (true) {
                    delay(SAVE_EVERY_MS)
                    if (player.isPlaying) save()
                }
            }
        }

        fun start(request: PlayRequest) {
            if (this.request != null) return
            this.request = request
            viewModelScope.launch { load(request) }
        }

        private suspend fun load(request: PlayRequest) {
            val details = repository.details(request.titleId)
            if (details == null) {
                fatal("Titlul nu mai există în playlist-uri.")
                return
            }
            title = details.title
            mutableState.update {
                it.copy(
                    title = details.title.name,
                    isSeries = details.title.mediaType == MediaType.TV,
                    introEndMs = seriesKey()?.let(settings::seriesIntro) ?: 0L,
                )
            }
            if (details.title.mediaType != MediaType.TV) searchAddonSubtitles(null, null)
            val watch = repository.watchState(request.titleId)
            if (details.title.mediaType == MediaType.TV) {
                val localEpisodes = repository.episodes(request.titleId)
                val episodes =
                    if (localEpisodes.seasons.isEmpty()) {
                        repository
                            .refreshEpisodes(request.titleId)
                            .map { repository.episodes(request.titleId) }
                            .firstOrNull { it.seasons.isNotEmpty() } ?: localEpisodes
                    } else {
                        localEpisodes
                    }
                seasons = episodes.seasons
                this.details = episodes.sources
                val all = seasons.flatMap { it.episodes }
                val episode =
                    (if (request.episode != null) EpisodeOrder.find(seasons, request.season, request.episode) else null)
                        ?: request.entryId?.let { id -> all.firstOrNull { e -> e.sources.any { it.id == id } } }
                        ?: watch
                            ?.takeIf { request.resume && request.episode == null && request.entryId == null }
                            ?.let { EpisodeOrder.find(seasons, it.season, it.episode) }
                        ?: EpisodeOrder.linear(seasons).firstOrNull()
                        ?: all.firstOrNull()
                if (episode == null) {
                    fatal("Nu am găsit episoade pentru acest serial.")
                    return
                }
                val savedEpisode =
                    episode.number?.takeIf { request.resume }?.let {
                        repository.episodeProgress(request.titleId, episode.season, it)
                    }
                val resumeAt =
                    when {
                        request.resume && watch != null && watch.season == episode.season &&
                            watch.episode == episode.number -> {
                            watch.positionMs
                        }

                        savedEpisode != null && Resume.shouldResume(savedEpisode.positionMs, savedEpisode.durationMs) &&
                            !savedEpisode.finished -> {
                            savedEpisode.positionMs
                        }

                        else -> {
                            0L
                        }
                    }
                playEpisode(episode, request.entryId, resumeAt, autoplay = !request.openTrackMenu)
            } else {
                this.details = details.sources
                val all = details.groups.flatMap { g -> g.languages.flatMap { it.sources } }
                if (all.isEmpty()) {
                    fatal("Filmul nu mai are surse în playlist-uri.")
                    return
                }
                val resumeAt = if (request.resume) watch?.positionMs ?: 0L else 0L
                play(all, request.entryId, resumeAt, autoplay = !request.openTrackMenu)
            }
        }

        private fun playEpisode(
            episode: MergedEpisode,
            chosen: Long?,
            positionMs: Long,
            autoplay: Boolean = true,
        ) {
            this.episode = episode
            val next = EpisodeOrder.next(seasons, episode.season, episode.number)
            mutableState.update {
                it.copy(subtitle = episodeLabel(episode), next = next?.let(::episodeLabel), countdown = null)
            }
            searchAddonSubtitles(episode.season, episode.number)
            play(episode.sources, chosen, positionMs, autoplay)
        }

        private fun play(
            list: List<SourceInfo>,
            chosen: Long?,
            positionMs: Long,
            autoplay: Boolean,
        ) {
            countdownJob?.cancel()
            sources = list.associateBy { it.id }
            val ordered = repository.playOrder(list, title?.originalLanguage).map { it.id }
            queue = FallbackQueue(ordered, chosen?.takeIf { it in sources })
            mutableState.update { it.copy(error = null, failures = emptyList(), countdown = null) }
            playCurrent(positionMs, autoplay)
        }

        private fun playCurrent(positionMs: Long, autoplay: Boolean = true) {
            val q = queue ?: return
            val id = q.current ?: return
            generation++
            val gen = generation
            started = false
            rebufferHistory = RebufferHistory()
            adaptiveSteps = 0
            retriedSame = false
            embeddedTextLanguages = null
            val resetVideoLimit =
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .build()
            player.trackSelectionParameters = PlayerFactory.apply(resetVideoLimit, trackPreferences())
            startPosition = positionMs
            player.playWhenReady = autoplay
            mutableState.update {
                it.copy(
                    loading = true,
                    sourceLabel = currentLabel(),
                    sources = sourceOptions(),
                    audio = emptyList(),
                    detectedResolution = null,
                )
            }
            viewModelScope.launch {
                val url = repository.streamUrl(id)
                if (gen != generation) return@launch
                if (url == null) {
                    sourceFailed("linkul nu se poate construi (playlist șters?)")
                    return@launch
                }
                player.setMediaItem(
                    MediaItem
                        .Builder()
                        .setUri(url)
                        .setMediaId(id.toString())
                        .build(),
                    positionMs,
                )
                player.prepare()
                if (player.playWhenReady) {
                    startWatchdog(START_TIMEOUT_MS, "nu pornește în ${START_TIMEOUT_MS / 1000} s")
                }
            }
        }

        private fun startWatchdog(timeoutMs: Long, reason: String, onTimeout: () -> Unit = { sourceFailed(reason) }) {
            watchdog?.cancel()
            val gen = generation
            watchdog =
                viewModelScope.launch {
                    delay(timeoutMs)
                    if (gen == generation && player.playWhenReady) onTimeout()
                }
        }

        private fun startStallWatchdog() {
            startWatchdog(STALL_TIMEOUT_MS, "bufferul nu se umple") {
                if (player.playbackState != Player.STATE_BUFFERING) return@startWatchdog
                val reason = "bufferul nu se umple"
                if (!recoverUnstableSource(reason) && !reconnectStalledSource()) sourceFailed(reason)
            }
        }

        private fun recoverUnstableSource(reason: String): Boolean {
            val current = player.videoFormat
            val bitrates =
                player.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected && it.isAdaptiveSupported }
                    .flatMap { group ->
                        (0 until group.length).filter(group::isTrackSupported).map { group.getTrackFormat(it).bitrate }
                    }
            val params = player.trackSelectionParameters
            val lower = PlaybackRecovery.lowerBitrate(current?.bitrate ?: 0, params.maxVideoBitrate, bitrates)
            if (lower != null) {
                player.trackSelectionParameters = params.buildUpon().setMaxVideoBitrate(lower).build()
                adaptiveSteps++
                showNotice("Redarea se întrerupe. Reduc bitrate-ul și păstrez aceeași sursă.")
                if (player.playbackState == Player.STATE_BUFFERING) startStallWatchdog()
                return true
            }
            if (queue?.hasMore != true) return false
            sourceFailed(reason, preferLighter = true)
            return true
        }

        /** O singură reconectare dacă ultima sursă s-a blocat după ce a redat deja. */
        private fun reconnectStalledSource(): Boolean {
            if (retriedSame) return false
            val item = player.currentMediaItem ?: return false
            val position = player.currentPosition.coerceAtLeast(0)
            retriedSame = true
            rebufferHistory.reset()
            showNotice("Bufferul nu se umple. Reconectez sursa de la aceeași poziție.")
            player.stop()
            player.setMediaItem(item, position)
            player.prepare()
            startStallWatchdog()
            return true
        }

        /** Sursa curentă nu merge: trecem la următoarea, de la aceeași poziție. */
        private fun sourceFailed(reason: String, preferLighter: Boolean = false) {
            val q = queue ?: return
            val failed = q.current ?: return
            watchdog?.cancel()
            val position = if (started) player.currentPosition.coerceAtLeast(0) else startPosition
            val autoplay = player.playWhenReady
            val preferred =
                if (preferLighter) {
                    val currentQuality =
                        VideoResolution.quality(player.videoSize.width, player.videoSize.height)
                            ?: sources[failed]?.quality
                    val available =
                        q.candidates
                            .filter {
                                it != failed && q.reasonFor(
                                    it,
                                ) == null
                            }.mapNotNull(sources::get)
                    PlaybackRecovery.lighterSource(currentQuality, sources[failed]?.languages.orEmpty(), available)
                } else {
                    null
                }
            val next = q.fail(reason, preferred)
            generation++
            player.stop()
            if (next == null) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error =
                            if (started) {
                                "Redarea s-a oprit. Nu mai sunt surse de rezervă."
                            } else if (q.size == 1) {
                                "Sursa nu a pornit."
                            } else {
                                "Niciuna din cele ${q.size} surse nu a pornit."
                            },
                        failures = q.failures.map { f -> "${label(f.entryId)}: ${f.reason}" },
                        sources = sourceOptions(),
                    )
                }
                return
            }
            showNotice("Sursa ${short(failed)}: $reason. Continui pe ${short(next)}.")
            playCurrent(position, autoplay)
        }

        /** Utilizatorul alege altă sursă din meniu: continuăm de la aceeași poziție. */
        fun selectSource(entryId: Long) {
            val q = queue ?: return
            if (entryId == q.current && mutableState.value.error == null) return
            val position = if (started) player.currentPosition.coerceAtLeast(0) else startPosition
            val autoplay = player.playWhenReady || mutableState.value.error != null
            if (!q.switchTo(entryId)) return
            mutableState.update { it.copy(error = null, failures = emptyList()) }
            playCurrent(position, autoplay)
        }

        /** Toate au eșuat: încercăm din nou lista, de la prima sursă. */
        fun retry() {
            val q = queue ?: return
            q.reset()
            mutableState.update { it.copy(error = null, failures = emptyList()) }
            playCurrent(startPosition)
        }

        fun selectTrack(option: TrackOption) {
            if (option.type == C.TRACK_TYPE_TEXT) {
                mutableState.update { it.copy(subtitlesOff = false) }
                subtitleDecided = true
                clearExternal()
            }
            val language = SubtitleLanguages.toIso1(option.group.getFormat(option.index).language)
            if (language != null) {
                seriesKey()?.let { key ->
                    settings.setSeriesTrack(
                        key,
                        if (option.type == C.TRACK_TYPE_AUDIO) "audio" else "text",
                        if (option.type == C.TRACK_TYPE_AUDIO) language else "embedded:$language",
                    )
                }
            }
            TrackOptions.select(player, option)
        }

        fun resetSeriesTracks() {
            seriesKey()?.let(settings::clearSeriesTracks)
            mutableState.update { it.copy(subtitlesOff = false) }
            clearExternal()
            player.trackSelectionParameters = PlayerFactory.apply(player.trackSelectionParameters, trackPreferences())
            subtitleDecided = false
            autoPickSubtitle()
            showNotice("Preferințele globale se aplică din nou acestui serial.")
        }

        fun disableSubtitles() {
            seriesKey()?.let { settings.setSeriesTrack(it, "text", "off") }
            mutableState.update { it.copy(subtitlesOff = true) }
            subtitleDecided = true
            clearExternal()
            TrackOptions.disableSubtitles(player)
        }

        fun automaticSubtitles() {
            seriesKey()?.let(settings::clearSeriesSubtitle)
            clearExternal()
            val languages = settings.state.value.subtitleLanguages
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setPreferredTextLanguages(*languages.toTypedArray())
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, languages.isEmpty())
                    .build()
            mutableState.update { it.copy(subtitlesOff = false) }
            subtitleDecided = false
            autoPickSubtitle()
        }

        /**
         * Caută subtitrări în addon-uri pentru titlul curent (filmul sau episodul dat). Subtitrarea din addon
         * a episodului anterior se scoate, iar pistele încorporate revin la preferințe.
         */
        private fun searchAddonSubtitles(
            season: Int?,
            number: Int?,
        ) {
            addonJob?.cancel()
            addonLoadJob?.cancel()
            addonResults = null
            subtitleDecided = false
            if (mutableDocument.value != null) {
                clearExternal()
                player.trackSelectionParameters =
                    PlayerFactory.apply(player.trackSelectionParameters, trackPreferences())
            }
            val title = title ?: return
            val series = title.mediaType == MediaType.TV
            mutableState.update {
                it.copy(
                    addonSubtitles = emptyList(),
                    subtitlesOff = seriesKey()?.let { settings.seriesTrack(it, "text") } == "off",
                    addonStatus = if (addons.addons.value.isEmpty()) null else "Caut subtitrări în addon-uri…",
                    subtitleOffsetMs = settings.state.value.subtitleDelayMs,
                )
            }
            if (addons.addons.value.isEmpty()) {
                addonResults = emptyList()
                return
            }
            addonJob =
                viewModelScope.launch {
                    val imdb = repository.imdbId(title)
                    val status: String?
                    val found: List<AddonSubtitle>
                    when {
                        imdb == null -> {
                            found = emptyList()
                            status =
                                if (title.isLocal) {
                                    "Titlul nu e potrivit pe TMDB, deci addon-urile nu-l pot căuta."
                                } else {
                                    "Nu știu ID-ul IMDb al titlului (cheia TMDB lipsește?)."
                                }
                        }

                        series && number == null -> {
                            found = emptyList()
                            status = "Episodul nu are număr, deci addon-urile nu-l pot căuta."
                        }

                        else -> {
                            val result = subtitleRepository.find(imdb, series, season, number)
                            found =
                                SubtitleChoice
                                    .order(result.subtitles, settings.state.value.subtitleLanguages)
                                    .take(MAX_ADDON_SUBTITLES)
                            status =
                                when {
                                    found.isNotEmpty() -> null
                                    result.problems.isNotEmpty() -> result.problems.joinToString("; ")
                                    else -> "Nicio subtitrare în addon-uri pentru acest titlu."
                                }
                        }
                    }
                    addonResults = found
                    mutableState.update { it.copy(addonSubtitles = addonLabels(found), addonStatus = status) }
                    autoPickSubtitle()
                }
        }

        /**
         * O singură dată pe titlu / episod, după ce știm pistele sursei și lista din addon-uri: dacă sursa nu are
         * subtitrare într-o limbă preferată la fel de bună, pornim cea mai bună din addon-uri.
         */
        private fun autoPickSubtitle() {
            if (subtitleDecided) return
            val embedded = embeddedTextLanguages ?: return
            val found = addonResults ?: return
            subtitleDecided = true
            val remembered = seriesKey()?.let { settings.seriesTrack(it, "text") }
            val selected =
                SeriesPlayback.subtitleChoice(
                    remembered,
                    embedded.mapNotNull(SubtitleLanguages::toIso1).toSet(),
                    found.mapNotNull { it.language }.toSet(),
                )
            if (selected == "off") {
                clearExternal()
                TrackOptions.disableSubtitles(player)
                return
            }
            val language = selected?.substringAfter(':')
            if (selected?.startsWith("embedded:") == true) {
                val track =
                    state.value.subtitles.firstOrNull {
                        it.supported && SubtitleLanguages.toIso1(it.group.getFormat(it.index).language) == language
                    }
                if (track != null) {
                    clearExternal()
                    TrackOptions.select(player, track)
                    return
                }
            }
            if (selected?.startsWith("addon:") == true) {
                found.firstOrNull { it.language == language }?.let {
                    loadAddonSubtitle(it, automatic = true)
                    return
                }
            }
            val prefs = settings.state.value
            if (!prefs.addonSubtitlesAuto) return
            val pick = SubtitleChoice.autoPick(embedded, found, prefs.subtitleLanguages) ?: return
            loadAddonSubtitle(pick, automatic = true)
        }

        /** Utilizatorul alege o subtitrare din addon-uri în meniu. */
        fun selectAddonSubtitle(option: AddonSubtitleOption) {
            subtitleDecided = true
            option.subtitle.language?.let { language ->
                seriesKey()?.let { settings.setSeriesTrack(it, "text", "addon:$language") }
            }
            loadAddonSubtitle(option.subtitle, automatic = false)
        }

        private fun loadAddonSubtitle(
            subtitle: AddonSubtitle,
            automatic: Boolean,
        ) {
            addonLoadJob?.cancel()
            val label =
                mutableState.value.addonSubtitles
                    .firstOrNull { it.subtitle.url == subtitle.url }
                    ?.label
            if (!automatic) showNotice("Se încarcă subtitrarea…")
            addonLoadJob =
                viewModelScope.launch {
                    try {
                        val document = subtitleRepository.load(subtitle)
                        mutableDocument.value = document
                        TrackOptions.disableSubtitles(player)
                        mutableState.update {
                            it.copy(
                                externalSubtitle = subtitle.url,
                                externalLabel = label,
                                subtitlesOff = false,
                            )
                        }
                        showNotice("Subtitrare: ${label ?: subtitle.addon}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showNotice("Subtitrarea nu se poate folosi (${SubtitleRepository.reason(e)}).")
                    }
                }
        }

        private fun clearExternal() {
            addonLoadJob?.cancel()
            mutableDocument.value = null
            mutableState.update { it.copy(externalSubtitle = null, externalLabel = null) }
        }

        /** Sincronizarea subtitrării din addon: + o întârzie, − o grăbește. */
        fun adjustSubtitleOffset(deltaMs: Long) {
            mutableState.update {
                it.copy(subtitleOffsetMs = (it.subtitleOffsetMs + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
            }
        }

        fun resetSubtitleOffset() {
            mutableState.update { it.copy(subtitleOffsetMs = 0) }
        }

        fun setSubtitleScale(percent: Int) = settings.setSubtitleScale(percent)

        fun setSubtitleStyle(style: SubtitleStyle) = settings.setSubtitleStyle(style)

        private fun trackPreferences(): TrackPreferences {
            val key = seriesKey()
            val audio = key?.let { settings.seriesTrack(it, "audio") }
            val text = key?.let { settings.seriesTrack(it, "text") }
            return TrackPreferences(
                SeriesPlayback.audioLanguages(audio, settings.audioLanguages(title?.originalLanguage)),
                if (text == "off") {
                    emptyList()
                } else {
                    listOfNotNull(text?.substringAfter(':')) + settings.state.value.subtitleLanguages
                },
            )
        }

        /** „Română 1 · OpenSubtitles v3”, „Română 2 · …”, „Engleză 1 · …”. */
        private fun addonLabels(list: List<AddonSubtitle>): List<AddonSubtitleOption> {
            val counts = HashMap<String, Int>()
            return list.map { sub ->
                val language = SubtitleLanguages.displayName(sub.rawLanguage.ifBlank { null })
                val n = (counts[language] ?: 0) + 1
                counts[language] = n
                AddonSubtitleOption(sub, "$language $n · ${sub.addon}")
            }
        }

        fun setBufferPreset(value: BufferPreset) {
            settings.setBufferPreset(value)
            showNotice("Buffer ${value.label}: se aplică la redeschiderea playerului.")
        }

        fun setNightMode(enabled: Boolean) {
            settings.setNightMode(enabled)
            showNotice("Modul noapte se aplică la redeschiderea playerului, cu audio PCM (fără passthrough).")
        }

        fun setAudioPassthrough(enabled: Boolean) {
            settings.setAudioPassthrough(enabled)
            showNotice("Passthrough se aplică la redeschiderea playerului; numai pe ieșiri compatibile.")
        }

        fun setMatchFrameRate(enabled: Boolean) = settings.setMatchFrameRate(enabled)

        private fun seriesKey(): String? =
            title?.takeIf { it.mediaType == MediaType.TV }?.let {
                SeriesPlayback.key(it.tmdbId, it.originalName ?: it.name, it.year)
            }

        fun markIntro() {
            val key = seriesKey() ?: return
            val position = player.currentPosition
            if (!SeriesPlayback.validIntro(position, player.duration)) {
                showNotice("Marchează sfârșitul introului între 1 secundă și 10 minute, înainte de finalul episodului.")
                return
            }
            settings.setSeriesIntro(key, position)
            mutableState.update { it.copy(introEndMs = position) }
            showNotice("Sfârșitul introului salvat pentru acest serial.")
        }

        fun clearIntro() {
            seriesKey()?.let { settings.setSeriesIntro(it, 0) }
            mutableState.update { it.copy(introEndMs = 0) }
        }

        fun skipIntro() {
            val end = state.value.introEndMs
            if (player.isCurrentMediaItemSeekable &&
                SeriesPlayback.canSkip(player.currentPosition, player.duration, end)
            ) {
                player.seekTo(end)
            }
        }

        fun setSleepTimer(minutes: Int) {
            sleepJob?.cancel()
            sleepTimer =
                if (minutes == -1 && state.value.isSeries) {
                    SleepTimer(endOfEpisode = true)
                } else {
                    SleepTimer.after(minutes, SystemClock.elapsedRealtime())
                }
            val label =
                when {
                    sleepTimer.endOfEpisode -> "Sfârșitul episodului"
                    sleepTimer.deadlineMs != null -> "$minutes min"
                    else -> "Oprit"
                }
            mutableState.update { it.copy(sleepLabel = label) }
            if (sleepTimer.endOfEpisode && player.playbackState == Player.STATE_ENDED) stopForSleep()
            sleepTimer.deadlineMs?.let { deadline ->
                sleepJob =
                    viewModelScope.launch {
                        delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        if (sleepTimer.shouldStop(SystemClock.elapsedRealtime(), false)) stopForSleep()
                    }
            }
        }

        private fun stopForSleep() {
            countdownJob?.cancel()
            watchdog?.cancel()
            generation++
            save()
            player.pause()
            mutableState.update { it.copy(finished = true, countdown = null) }
        }

        fun technicalInfo(): String {
            val video = player.videoFormat
            val audio = player.audioFormat
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()

            fun bitrate(value: Int?): String =
                value?.takeIf { it > 0 }?.let { "${it / 1000} kb/s (declarat)" } ?: "necunoscut"
            val hdr =
                when (video?.colorInfo?.colorTransfer) {
                    C.COLOR_TRANSFER_ST2084 -> "HDR PQ"
                    C.COLOR_TRANSFER_HLG -> "HDR HLG"
                    else -> "SDR / nespecificat"
                }
            return listOf(
                "Video: ${video?.sampleMimeType ?: "necunoscut"} · ${video?.codecs ?: "codec nespecificat"}",
                "Rezoluție: ${video?.width?.takeIf {
                    it > 0
                } ?: "?"} × ${video?.height?.takeIf { it > 0 } ?: "?"} · $hdr",
                "Cadre/s: ${video?.frameRate?.takeIf { it > 0 } ?: "?"} · viteză ${player.playbackParameters.speed}×",
                "Bitrate video: ${bitrate(video?.averageBitrate)}",
                "Audio: ${audio?.sampleMimeType ?: "necunoscut"} · ${audio?.channelCount?.takeIf {
                    it > 0
                } ?: "?"} canale · ${audio?.sampleRate?.takeIf { it > 0 } ?: "?"} Hz",
                "Bitrate audio: ${bitrate(audio?.averageBitrate)}",
                "Cadre pierdute: ${counters?.droppedBufferCount ?: 0} (sursa curentă)",
                "Buffer disponibil: ${player.totalBufferedDuration.coerceAtLeast(0) / 1000} s",
                "Întreruperi de buffer: ${rebufferHistory.totalCount} · ${Resume.format(
                    rebufferHistory.totalDurationMs,
                )} total",
                "Reduceri de bitrate pe sursa curentă: $adaptiveSteps",
                "Passthrough solicitat la deschidere: ${if (passthroughRequested) "da, dacă ieșirea permite" else "nu"}",
                "Compresie PCM: ${if (nightModeEnabled) "mod noapte" else "dezactivată"}",
            ).joinToString("\n")
        }

        fun setPictureMode(mode: Int) {
            mutableState.update { it.copy(pictureMode = mode.coerceIn(0, 2)) }
        }

        fun setSpeed(speed: Float) {
            if (speed !in listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) return
            runCatching { player.setPlaybackSpeed(speed) }
                .onSuccess { mutableState.update { it.copy(speed = player.playbackParameters.speed) } }
                .onFailure {
                    showNotice(
                        "Viteza nu este acceptată de ieșirea audio. Dezactivează passthrough și redeschide playerul.",
                    )
                }
        }

        fun togglePlay() = setPlaying(!player.playWhenReady || player.playbackState == Player.STATE_ENDED)

        fun setPlaying(playing: Boolean) {
            if (playing && player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.playWhenReady = playing
        }

        fun seekBy(deltaMs: Long) {
            if (!player.isCurrentMediaItemSeekable) {
                showNotice("Sursa nu permite derularea.")
                return
            }
            val target = SeekNavigation.target(player.currentPosition, deltaMs, player.duration) ?: return
            player.seekTo(target)
        }

        fun seekHeld(direction: Int, heldMs: Long) {
            val step = SeekNavigation.increment(heldMs)
            seekBy(step * direction)
            showNotice("${if (direction > 0) "+" else "−"}${step / 1000} s · ${Resume.format(player.currentPosition)}")
        }

        fun jumpToMinute(text: String): Boolean {
            val position = SeekNavigation.minute(text, player.duration)
            if (position == null || !player.isCurrentMediaItemSeekable) {
                showNotice("Introdu un minut din durata filmului; sursa trebuie să permită derularea.")
                return false
            }
            player.seekTo(position)
            return true
        }

        /** Episodul următor acum (buton sau la finalul numărătorii). */
        fun playNext() {
            val current = episode ?: return
            val next = EpisodeOrder.next(seasons, current.season, current.number) ?: return
            save()
            playEpisode(next, null, 0L)
        }

        fun cancelCountdown() {
            countdownJob?.cancel()
            mutableState.update { it.copy(countdown = null) }
        }

        /** Ecranul nu mai e vizibil (ex. butonul Home): pauză și salvăm progresul. */
        fun onStop() {
            save()
            player.pause()
        }

        private fun onEnded() {
            if (sleepTimer.shouldStop(SystemClock.elapsedRealtime(), true)) {
                stopForSleep()
                return
            }
            watchdog?.cancel()
            save()
            val current = episode
            val next = current?.let { EpisodeOrder.next(seasons, it.season, it.number) }
            if (next == null) {
                mutableState.update { it.copy(finished = true) }
                return
            }
            if (!settings.state.value.autoNext) return
            countdownJob?.cancel()
            countdownJob =
                viewModelScope.launch {
                    for (s in NEXT_COUNTDOWN_S downTo 1) {
                        mutableState.update { it.copy(countdown = s) }
                        delay(1_000)
                    }
                    mutableState.update { it.copy(countdown = null) }
                    playNext()
                }
        }

        /** Salvează unde a rămas utilizatorul (sau șterge, dacă filmul / serialul s-a terminat). */
        private fun save() {
            val title = title ?: return
            val entryId = queue?.current ?: return
            if (!started) return
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
            val position = player.currentPosition.coerceAtLeast(0)
            val finished = Resume.isFinished(position, duration)
            val now = System.currentTimeMillis()
            val current = episode
            val next = current?.let { EpisodeOrder.next(seasons, it.season, it.number) }
            val nextEntry = next?.let { repository.playOrder(it.sources, title.originalLanguage).firstOrNull()?.id }
            saveScope.launch {
                if (finished || position >= Resume.MIN_POSITION_MS) {
                    val episodeProgress =
                        current?.let { e ->
                            e.number?.let {
                                EpisodeProgressEntity(
                                    title.id,
                                    e.season,
                                    it,
                                    position,
                                    duration,
                                    finished,
                                    now,
                                )
                            }
                        }
                    repository.recordWatch(title.id, position.toDouble() / duration, finished, now, episodeProgress)
                }
                when {
                    !finished && position >= Resume.MIN_POSITION_MS -> {
                        repository.saveWatchState(
                            WatchStateEntity(
                                titleId = title.id,
                                entryId = entryId,
                                season = current?.season,
                                episode = current?.number,
                                positionMs = position,
                                durationMs = duration,
                                updatedAt = now,
                            ),
                        )
                    }

                    finished && next != null && nextEntry != null -> {
                        repository.saveWatchState(
                            WatchStateEntity(
                                titleId = title.id,
                                entryId = nextEntry,
                                season = next.season,
                                episode = next.number,
                                positionMs = 0,
                                durationMs = 0,
                                updatedAt = now,
                            ),
                        )
                    }

                    finished -> {
                        repository.clearWatchState(title.id)
                    }

                    else -> {}
                }
            }
        }

        private fun fatal(message: String) {
            mutableState.update { it.copy(loading = false, error = message) }
        }

        private fun showNotice(text: String) {
            noticeJob?.cancel()
            mutableState.update { it.copy(notice = text) }
            noticeJob =
                viewModelScope.launch {
                    delay(NOTICE_MS)
                    mutableState.update { it.copy(notice = null) }
                }
        }

        private fun label(entryId: Long): String =
            sources[entryId]?.let { sourceLabel(it, details[entryId]) } ?: "Sursa $entryId"

        private fun short(entryId: Long): String = sources[entryId]?.let(::sourceShortLabel) ?: "Sursa"

        private fun currentLabel(): String? {
            val q = queue ?: return null
            val id = q.current ?: return null
            val prefix = if (q.size > 1) "Sursa ${q.position} din ${q.size} · " else ""
            return prefix + label(id)
        }

        private fun sourceOptions(): List<SourceOption> {
            val q = queue ?: return emptyList()
            return q.candidates.map { id -> SourceOption(id, label(id), q.reasonFor(id), id == q.current) }
        }

        override fun onCleared() {
            save()
            addonJob?.cancel()
            addonLoadJob?.cancel()
            watchdog?.cancel()
            countdownJob?.cancel()
            sleepJob?.cancel()
            player.removeListener(listener)
            player.release()
        }

        companion object {
            const val START_TIMEOUT_MS = 25_000L
            const val STALL_TIMEOUT_MS = 16_000L
            const val SAVE_EVERY_MS = 15_000L
            const val NOTICE_MS = 7_000L
            const val NEXT_COUNTDOWN_S = 10
            const val MAX_ADDON_SUBTITLES = 80
            const val MAX_OFFSET_MS = 600_000L

            fun episodeLabel(episode: MergedEpisode): String {
                val code =
                    if (episode.number != null) "S%02dE%02d".format(episode.season, episode.number) else null
                return listOfNotNull(code, episode.title).joinToString(" · ").ifEmpty { "Episod" }
            }
        }
    }
