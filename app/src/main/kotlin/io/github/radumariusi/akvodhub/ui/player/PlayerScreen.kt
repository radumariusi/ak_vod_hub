@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.player.PlayerFactory
import io.github.radumariusi.akvodhub.player.PlayerUiState
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import kotlinx.coroutines.delay

/**
 * Ecranul de redare. Fără meniu pe ecran: stânga/dreapta derulează 10 s, OK sau sus/jos arată comenzile
 * (pauză, audio, subtitrări, sursă, episodul următor). Comenzile se ascund singure după 5 s.
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
    LaunchedEffect(controls, lastInput, isPlaying, choice) {
        if (controls && isPlaying && choice == null) {
            delay(HIDE_AFTER_MS)
            controls = false
        }
    }
    LaunchedEffect(state.finished) { if (state.finished) onExit() }
    val overlay = state.error != null || state.countdown != null
    LaunchedEffect(controls, overlay) {
        if (!overlay) runCatching { if (controls) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    BackHandler {
        when {
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
                        viewModel.seekBy(PlayerFactory.SEEK_MS * 3)
                        true
                    }

                    Key.MediaRewind -> {
                        viewModel.seekBy(-PlayerFactory.SEEK_MS * 3)
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
                        viewModel.seekBy(-PlayerFactory.SEEK_MS)
                        controls = true
                        true
                    }

                    Key.DirectionRight -> {
                        viewModel.seekBy(PlayerFactory.SEEK_MS)
                        controls = true
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
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

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
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        if (state.error != null) {
            ErrorPanel(state, viewModel, onExit, onChoice = { choice = it }, Modifier.align(Alignment.Center))
        } else if (state.countdown != null) {
            NextPanel(state, viewModel, Modifier.align(Alignment.BottomEnd))
        }
    }

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
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.85f)))
                .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 32.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::togglePlay, modifier = Modifier.focusRequester(playFocus)) {
                Text(if (isPlaying) "Pauză" else "Redă")
            }
            OutlinedButton(onClick = { viewModel.seekBy(-PlayerFactory.SEEK_MS) }) { Text("−10 s") }
            OutlinedButton(onClick = { viewModel.seekBy(PlayerFactory.SEEK_MS) }) { Text("+10 s") }
            if (state.audio.isNotEmpty()) {
                OutlinedButton(onClick = {
                    onChoice(
                        Choice(
                            "Audio",
                            state.audio.map { it.label },
                            state.audio.indexOfFirst { it.selected }.takeIf { it >= 0 },
                        ) { i -> viewModel.selectTrack(state.audio[i]) },
                    )
                }) { Text("Audio (${state.audio.size})") }
            }
            OutlinedButton(onClick = {
                val selected = state.subtitles.indexOfFirst { it.selected }
                onChoice(
                    Choice(
                        "Subtitrări",
                        listOf("Oprite") + state.subtitles.map { it.label },
                        selected + 1,
                    ) { i ->
                        if (i == 0) {
                            viewModel.disableSubtitles()
                        } else {
                            viewModel.selectTrack(state.subtitles[i - 1])
                        }
                    },
                )
            }) { Text(if (state.subtitles.isEmpty()) "Subtitrări" else "Subtitrări (${state.subtitles.size})") }
            if (state.sources.size > 1) {
                OutlinedButton(onClick = { onChoice(sourceChoice(state, viewModel)) }) {
                    Text("Sursă (${state.sources.size})")
                }
            }
            if (state.next != null) {
                OutlinedButton(onClick = viewModel::playNext) { Text("Episodul următor") }
            }
        }
    }
}

/** Bara de progres: când are focus, stânga/dreapta derulează câte 10 s. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 6.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> {
                            onSeek(-PlayerFactory.SEEK_MS)
                            true
                        }

                        Key.DirectionRight -> {
                            onSeek(PlayerFactory.SEEK_MS)
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
