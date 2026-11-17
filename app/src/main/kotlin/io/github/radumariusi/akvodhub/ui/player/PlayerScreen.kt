@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.R
import io.github.radumariusi.akvodhub.parser.player.BufferPreset
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.parser.player.SeekNavigation
import io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
import io.github.radumariusi.akvodhub.player.PlayerFactory
import io.github.radumariusi.akvodhub.player.PlayerUiState
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.delay

/**
 * Ecranul de redare. Fără meniu pe ecran: stânga/dreapta derulează 10 s, OK sau sus/jos arată comenzile
 * (pauză, audio, subtitrări, sursă, episodul următor). Comenzile se ascund singure după 5 s.
 * Pe telefon: atingerea imaginii arată/ascunde comenzile, iar bara de progres se atinge sau se trage.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val player = viewModel.player
    var controls by remember { mutableStateOf(false) }
    var lastInput by remember { mutableIntStateOf(0) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var technical by remember { mutableStateOf(false) }
    var jumping by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val prefs by viewModel.prefs.collectAsState()
    val document by viewModel.externalDocument.collectAsState()
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    LaunchedEffect(controls, lastInput, isPlaying, choice, adjusting, jumping, technical) {
        if (controls && isPlaying && choice == null && !adjusting && !jumping && !technical) {
            delay(HIDE_AFTER_MS)
            controls = false
        }
    }
    LaunchedEffect(state.finished) { if (state.finished) onExit() }
    val overlay = state.error != null || state.countdown != null || adjusting
    LaunchedEffect(controls, overlay) {
        if (!overlay) runCatching { if (controls) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            adjusting -> adjusting = false
            controls -> controls = false
            state.countdown != null -> viewModel.cancelCountdown()
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput++
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePlay()
                        true
                    }

                    Key.MediaFastForward -> {
                        viewModel.seekHeld(1, e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime)
                        true
                    }

                    Key.MediaRewind -> {
                        viewModel.seekHeld(-1, e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime)
                        true
                    }

                    Key.MediaNext -> {
                        viewModel.playNext()
                        true
                    }

                    else -> {
                        false
                    }
                }
            }.onKeyEvent { e ->
                // Doar când comenzile sunt ascunse: altfel tastele mută focusul între butoane.
                if (controls || overlay || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> {
                        viewModel.seekHeld(-1, e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime)
                        true
                    }

                    Key.DirectionRight -> {
                        viewModel.seekHeld(1, e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime)
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                        controls = true
                        true
                    }

                    else -> {
                        false
                    }
                }
            }.focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    isFocusable = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    this.player = player
                }
            },
            update = { view ->
                SubtitleLook.applyTo(view, prefs.subtitleScale, prefs.subtitleStyle)
                view.resizeMode =
                    when (state.pictureMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // Touch (telefon): o atingere pe imagine arată/ascunde comenzile. Strat separat peste PlayerView,
        // ca atingerile să nu se piardă în View-ul Android de dedesubt.
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures {
                    lastInput++
                    controls = !controls
                }
            },
        )

        document?.let {
            ExternalSubtitles(
                document = it,
                player = player,
                offsetMs = state.subtitleOffsetMs,
                scale = prefs.subtitleScale,
                style = prefs.subtitleStyle,
                raised = controls && !overlay,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (state.loading && state.error == null) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 720.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Se încarcă…", style = MaterialTheme.typography.titleLarge, color = Color.White)
                state.sourceLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        state.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp)
                        .widthIn(max = 900.dp)
                        .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (controls && !overlay) {
            Controls(
                state = state,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                playFocus = playFocus,
                viewModel = viewModel,
                onInput = { lastInput++ },
                onChoice = { choice = it },
                onAdjust = { adjusting = true },
                onJump = { jumping = true },
                onTechnical = { technical = true },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        if (adjusting) {
            SubtitleAdjustPanel(
                state = state,
                scale = prefs.subtitleScale,
                style = prefs.subtitleStyle,
                viewModel = viewModel,
                onClose = { adjusting = false },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (state.error != null) {
            ErrorPanel(state, viewModel, onExit, onChoice = { choice = it }, Modifier.align(Alignment.Center))
        } else if (state.countdown != null) {
            NextPanel(state, viewModel, Modifier.align(Alignment.BottomEnd))
        }
    }

    if (technical) TechnicalInfoDialog(viewModel, onClose = { technical = false })
    if (jumping) JumpToMinuteDialog(viewModel, onClose = { jumping = false })
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}

@Composable
private fun Controls(
    state: PlayerUiState,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playFocus: FocusRequester,
    viewModel: PlayerViewModel,
    onInput: () -> Unit,
    onChoice: (Choice) -> Unit,
    onAdjust: () -> Unit,
    onJump: () -> Unit,
    onTechnical: () -> Unit,
    modifier: Modifier,
) {
    val playbackPrefs by viewModel.prefs.collectAsState()
    var focusedCommand by remember { mutableStateOf("") }
    val onCommandFocus: (String) -> Unit = { focusedCommand = it }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.85f)))
                // Orice atingere pe comenzi amână ascunderea lor (ca o tastă apăsată pe telecomandă).
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onInput()
                        }
                    }
                }.padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            state.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White) }
        state.sourceLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SeekBar(position, duration, onSeek = {
            viewModel.seekBy(it)
            onInput()
        })
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCommand(
                if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                if (isPlaying) "Pauză" else "Redă",
                onCommandFocus,
                modifier = Modifier.focusRequester(playFocus),
                onClick = viewModel::togglePlay,
            )
            PlayerCommand(R.drawable.ic_player_rewind, "Înapoi 10 secunde", onCommandFocus) {
                viewModel.seekBy(-PlayerFactory.SEEK_MS)
            }
            PlayerCommand(R.drawable.ic_player_forward, "Înainte 10 secunde", onCommandFocus) {
                viewModel.seekBy(PlayerFactory.SEEK_MS)
            }
            if (state.audio.isNotEmpty()) {
                PlayerCommand(R.drawable.ic_player_audio, "Audio (${state.audio.size})", onCommandFocus) {
                    onChoice(
                        Choice(
                            "Audio",
                            state.audio.map { it.label },
                            state.audio.indexOfFirst { it.selected }.takeIf { it >= 0 },
                        ) { i -> viewModel.selectTrack(state.audio[i]) },
                    )
                }
            }
            val subtitleCount = state.subtitles.size + state.addonSubtitles.size
            PlayerCommand(R.drawable.ic_player_subtitles, if (subtitleCount == 0) "Subtitrări" else "Subtitrări ($subtitleCount)", onCommandFocus) { onChoice(subtitleChoice(state, viewModel)) }
            if (state.externalSubtitle != null || state.subtitles.any { it.selected }) {
                PlayerCommand(R.drawable.ic_player_adjust, "Ajustează subtitrările", onCommandFocus, onClick = onAdjust)
            }
            if (state.sources.size > 1) {
                PlayerCommand(R.drawable.ic_player_source, "Sursă (${state.sources.size})", onCommandFocus) { onChoice(sourceChoice(state, viewModel)) }
            }
            PlayerCommand(R.drawable.ic_player_screen, "Frecvență ecran", onCommandFocus) {
                onChoice(
                    Choice(
                        "Frecvență ecran · poate clipi la schimbare",
                        listOf("Dezactivată", "Potrivește cu filmul"),
                        if (playbackPrefs.matchFrameRate) 1 else 0,
                    ) {
                        viewModel.setMatchFrameRate(it == 1)
                    },
                )
            }
            PlayerCommand(R.drawable.ic_player_output, "Ieșire audio · passthrough", onCommandFocus) {
                onChoice(
                    Choice(
                        "Passthrough · la redeschiderea playerului",
                        listOf("Dezactivat · PCM", "Automat · ieșire compatibilă"),
                        if (playbackPrefs.audioPassthrough) 1 else 0,
                    ) {
                        viewModel.setAudioPassthrough(it == 1)
                    },
                )
            }
            PlayerCommand(R.drawable.ic_player_night, "Mod noapte", onCommandFocus) {
                onChoice(
                    Choice(
                        "Mod noapte · la redeschidere, fără passthrough",
                        listOf("Dezactivat", "Dialog mai clar · compresie PCM"),
                        if (playbackPrefs.nightMode) 1 else 0,
                    ) {
                        viewModel.setNightMode(it == 1)
                    },
                )
            }
            PlayerCommand(R.drawable.ic_player_format, "Format imagine", onCommandFocus) {
                onChoice(
                    Choice(
                        "Format imagine",
                        listOf("Potrivit", "Zoom", "Întins"),
                        state.pictureMode,
                        viewModel::setPictureMode,
                    ),
                )
            }
            PlayerCommand(R.drawable.ic_player_speed, "Viteză ${state.speed}×", onCommandFocus) {
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                onChoice(
                    Choice(
                        "Viteză de redare",
                        listOf("0,75×", "1×", "1,25×", "1,5×", "1,75×", "2×"),
                        speeds.indexOf(state.speed),
                    ) {
                        viewModel.setSpeed(speeds[it])
                    },
                )
            }
            PlayerCommand(R.drawable.ic_player_buffer, "Buffer", onCommandFocus) {
                onChoice(
                    Choice(
                        "Buffer · la redeschiderea playerului",
                        BufferPreset.entries.map {
                            it.label
                        },
                        BufferPreset.entries.indexOf(playbackPrefs.bufferPreset),
                    ) {
                        viewModel.setBufferPreset(BufferPreset.entries[it])
                    },
                )
            }
            PlayerCommand(R.drawable.ic_player_timer, "Timer de somn · ${state.sleepLabel}", onCommandFocus) {
                val minutes = if (state.isSeries) listOf(0, 30, 60, 90, -1) else listOf(0, 30, 60, 90)
                val labels =
                    listOf("Oprit", "30 minute", "60 minute", "90 minute") +
                        if (state.isSeries) listOf("Sfârșitul episodului") else emptyList()
                onChoice(
                    Choice(
                        "Timer de somn · ${state.sleepLabel}",
                        labels,
                        null,
                    ) { viewModel.setSleepTimer(minutes[it]) },
                )
            }
            PlayerCommand(R.drawable.ic_player_info, "Info tehnic", onCommandFocus, onClick = onTechnical)
            PlayerCommand(R.drawable.ic_player_timer, "Salt la minut", onCommandFocus, onClick = onJump)
            if (state.isSeries) {
                PlayerCommand(R.drawable.ic_player_reset, "Resetează audio/subtitrare serial", onCommandFocus, onClick = viewModel::resetSeriesTracks)
                if (SeriesPlayback.canSkip(position, duration, state.introEndMs)) {
                    PlayerCommand(R.drawable.ic_player_next, "Sari intro", onCommandFocus, onClick = viewModel::skipIntro)
                }
                PlayerCommand(R.drawable.ic_player_bookmark, "Marcaj intro serial", onCommandFocus) {
                    onChoice(
                        Choice(
                            "Intro pentru acest serial",
                            listOf("Marchează sfârșitul aici", "Șterge marcajul"),
                            null,
                        ) {
                            if (it == 0) viewModel.markIntro() else viewModel.clearIntro()
                        },
                    )
                }
            }
            if (state.next != null) {
                PlayerCommand(R.drawable.ic_player_next, "Episodul următor", onCommandFocus, onClick = viewModel::playNext)
            }
        }
        Text(
            focusedCommand,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerCommand(
    icon: Int,
    label: String,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, label) { if (focused) onFocus(label) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).onFocusChanged { focused = it.isFocused },
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

/** Bara de progres: când are focus, stânga/dreapta derulează câte 10 s; pe telefon se atinge sau se trage. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Poziția trasă cu degetul, aplicată la ridicare (fără căutări repetate în timpul tragerii).
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)
    val seekTo = { f: Float ->
        if (currentDuration > 0) onSeek((f.coerceIn(0f, 1f) * currentDuration).toLong() - currentPosition)
    }
    val fraction =
        dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { seekTo(it.x / size.width) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let(seekTo)
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                // Zonă de atingere mai înaltă decât bara desenată.
                .padding(vertical = 12.dp)
                .height(if (focused) 10.dp else 6.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> {
                            onSeek(-SeekNavigation.increment(e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime))
                            true
                        }

                        Key.DirectionRight -> {
                            onSeek(SeekNavigation.increment(e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime))
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusable()
                .background(Color.White.copy(alpha = 0.25f), MaterialTheme.shapes.small)
                .then(if (focused) Modifier.border(1.dp, Color.White, MaterialTheme.shapes.small) else Modifier),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 10.dp else 6.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            )
        }
        Text(
            if (duration > 0) "${Resume.format(position)} / ${Resume.format(duration)}" else Resume.format(position),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun ErrorPanel(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    onChoice: (Choice) -> Unit,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.error) { runCatching { focus.requestFocus() } }
    Column(
        modifier
            .widthIn(max = 900.dp)
            .background(Color.Black.copy(alpha = 0.85f), MaterialTheme.shapes.large)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.error.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        state.failures.take(MAX_FAILURES_SHOWN).forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.failures.size > MAX_FAILURES_SHOWN) {
            Text(
                "… și încă ${state.failures.size - MAX_FAILURES_SHOWN}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sources.isNotEmpty()) {
                Button(onClick = viewModel::retry, modifier = Modifier.focusRequester(focus)) {
                    Text("Încearcă din nou")
                }
                if (state.sources.size > 1) {
                    OutlinedButton(onClick = { onChoice(sourceChoice(state, viewModel)) }) { Text("Alege sursa") }
                }
                OutlinedButton(onClick = onExit) { Text("Înapoi") }
            } else {
                Button(onClick = onExit, modifier = Modifier.focusRequester(focus)) { Text("Înapoi") }
            }
        }
    }
}

@Composable
private fun NextPanel(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier
            .padding(40.dp)
            .widthIn(max = 520.dp)
            .background(Color.Black.copy(alpha = 0.85f), MaterialTheme.shapes.large)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Urmează în ${state.countdown} s", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(
            state.next.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::playNext, modifier = Modifier.focusRequester(focus)) { Text("Redă acum") }
            OutlinedButton(onClick = viewModel::cancelCountdown) { Text("Anulează") }
        }
    }
}

/** Meniul „Subtitrări”: oprite, pistele din sursă, apoi cele din addon-uri (limbile preferate primele). */
private fun subtitleChoice(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
): Choice {
    val embedded = state.subtitles
    val addon = state.addonSubtitles
    val options =
        listOf("Oprite") +
            embedded.map { "Din sursă · ${it.label}" } +
            addon.map { it.label } +
            listOfNotNull(state.addonStatus?.let { "ℹ $it" })
    val selected =
        when {
            state.externalSubtitle != null -> {
                addon
                    .indexOfFirst { it.subtitle.url == state.externalSubtitle }
                    .takeIf { it >= 0 }
                    ?.let { 1 + embedded.size + it }
            }

            embedded.any { it.selected } -> {
                1 + embedded.indexOfFirst { it.selected }
            }

            else -> {
                0
            }
        }
    return Choice("Subtitrări", options, selected) { i ->
        when {
            i == 0 -> viewModel.disableSubtitles()
            i <= embedded.size -> viewModel.selectTrack(embedded[i - 1])
            i <= embedded.size + addon.size -> viewModel.selectAddonSubtitle(addon[i - 1 - embedded.size])
            else -> Unit
        }
    }
}

private fun sourceChoice(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
) = Choice(
    "Sursă",
    state.sources.map { s ->
        when {
            s.current && state.error == null -> "▶ ${s.label}"
            s.failedReason != null -> "✗ ${s.label} (${s.failedReason})"
            else -> s.label
        }
    },
    state.sources.indexOfFirst { it.current }.takeIf { it >= 0 },
) { i -> viewModel.selectSource(state.sources[i].entryId) }

private const val HIDE_AFTER_MS = 5_000L
private const val MAX_FAILURES_SHOWN = 6
