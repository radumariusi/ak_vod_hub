@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.db.ContinueItem
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.playlist.LibraryTotals
import io.github.radumariusi.akvodhub.hub.HomeRow
import io.github.radumariusi.akvodhub.hub.HomeViewModel
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.player.PlayRequest
import io.github.radumariusi.akvodhub.player.PlayerActivity
import io.github.radumariusi.akvodhub.ui.formatCount
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.delay

/**
 * Acasă: în sus, fișa titlului cu focus (fundal, descriere); dedesubt, rânduri orizontale
 * (Continuă vizionarea, Adăugate recent, Populare, Apreciate, pe genuri), fiecare paginat.
 */
@Composable
fun HomeScreen(
    totals: LibraryTotals?,
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpen: (TitleCard, String) -> Unit,
    restoreKey: String?,
    onRestored: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    when {
        totals != null && totals.playlists == 0 -> {
            EmptyLibrary(onOpenSettings)
        }

        state.rows.isEmpty() -> {
            Message(
                if (!state.loading && totals?.movies == 0 && totals.series == 0) {
                    "Playlist-ul nu are încă titluri disponibile."
                } else if (state.loading || totals == null) {
                    "Se încarcă biblioteca…"
                } else {
                    "Biblioteca se pregătește: filmele și serialele apar aici după import " +
                        "(${formatCount(totals.movies)} filme, ${formatCount(totals.series)} seriale în playlist-uri)."
                },
            )
        }

        else -> {
            HomeRows(state.rows, viewModel, onOpen, restoreKey, onRestored)
        }
    }
}

@Composable
private fun HomeRows(
    rows: List<HomeRow>,
    viewModel: HomeViewModel,
    onOpen: (TitleCard, String) -> Unit,
    restoreKey: String?,
    onRestored: () -> Unit,
) {
    // Focusul inițial (o singură dată) pe primul titlu; apoi, la întoarcere, pe cardul deschis.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val focusKey = restoreKey ?: if (!initialFocusDone) cardKey(rows[0], rows[0].items[0]) else null
    val focused = remember { mutableStateOf<TitleCard?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var returnToCard by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun play(item: ContinueItem, resume: Boolean) {
        context.startActivity(PlayerActivity.intent(context, PlayRequest(item.titleId, season = item.season, episode = item.episode, resume = resume)))
    }

    fun actions(card: TitleCard, key: String, info: ContinueItem) {
        returnToCard = key
        choice = Choice("Continuă vizionarea", listOf("Continuă", "Detalii", "De la început", "Scoate din continuare"), 0) {
            when (it) {
                0 -> play(info, resume = true)
                1 -> onOpen(card, key)
                2 -> play(info, resume = false)
                3 -> {
                    returnToCard = null
                    viewModel.removeContinue(card.id)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val extra = if (rows.any { it.key == HomeViewModel.CONTINUE }) 64.dp else 0.dp
        val heroHeight = (maxHeight - PosterHeight - 108.dp - extra).coerceIn(0.dp, 200.dp)
        Column(Modifier.fillMaxSize()) {
        val prefs by viewModel.prefs.collectAsState()
        if (prefs.homeHero && heroHeight >= 104.dp) {
            Hero(focused, fallback = rows[0].items[0], logo = viewModel::logo, height = heroHeight)
        }
        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rows, key = { it.key }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        state = rememberLazyListState(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(row.items, key = { _, card -> card.id }) { index, card ->
                            val key = cardKey(row, card)
                            val info = row.continueInfo[card.id]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PosterCard(
                                    card = card,
                                    onClick = {
                                        when {
                                            info == null -> onOpen(card, key)
                                            prefs.continueDirect -> play(info, resume = true)
                                            else -> actions(card, key, info)
                                        }
                                    },
                                    onFocused = {
                                        focused.value = card
                                        viewModel.onItemFocused(row.key, index)
                                    },
                                    requestFocus = key == focusKey || (choice == null && key == returnToCard),
                                    onFocusRequested = {
                                        initialFocusDone = true
                                        returnToCard = null
                                        onRestored()
                                    },
                                    supportingText = info?.progressLabel(),
                                )
                                if (info != null && prefs.continueDirect) {
                                    OutlinedButton(onClick = { actions(card, key, info) }) { Text("Acțiuni") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }

}

private fun ContinueItem.progressLabel(): String? =
    listOfNotNull(
        if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null,
        if (positionMs > 0 && durationMs > positionMs) "${Resume.format(durationMs - positionMs)} rămase" else null,
    ).joinToString(" · ").ifBlank { null }

private fun cardKey(
    row: HomeRow,
    card: TitleCard,
) = "home:${row.key}:${card.id}"

/** Fișa titlului cu focus. Se schimbă cu o mică întârziere, ca derularea rapidă să nu încarce fiecare fundal. */
@Composable
private fun Hero(
    focused: MutableState<TitleCard?>,
    fallback: TitleCard,
    logo: suspend (Long) -> String?,
    height: Dp,
) {
    val target = focused.value ?: fallback
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        delay(HERO_DELAY_MS)
        shown = target
    }
    var logoPath by remember(shown.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(shown.id) { logoPath = logo(shown.id) }
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(height)) {
        val backdrop = Images.hero(shown.backdropPath)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.62f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.35f to background,
                    0.75f to background.copy(alpha = 0.4f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to background)))
        Column(
            modifier = Modifier.widthIn(max = 520.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TitleHeading(shown.name, logoPath)
            val meta = shown.meta()
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.overview?.takeIf { height >= 130.dp }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = ((height.value.toInt() - 105) / 20).coerceIn(1, 4),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onOpenSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AK VOD Hub", style = MaterialTheme.typography.displaySmall)
        Text(
            "Încă nu ai niciun playlist. Adaugă din Setări un cont Xtream sau un playlist M3U; " +
                "păstrăm doar filmele și serialele.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Button(onClick = onOpenSettings, modifier = Modifier.focusRequester(focus)) {
            Text("Adaugă un playlist")
        }
    }
}

@Composable
fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 640.dp).padding(top = 16.dp),
    )
}

private const val HERO_DELAY_MS = 300L
