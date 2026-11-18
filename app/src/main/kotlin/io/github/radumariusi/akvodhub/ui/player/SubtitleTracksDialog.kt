package io.github.radumariusi.akvodhub.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlayerUiState
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.ListItem

@Composable
internal fun SubtitleTracksDialog(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val embeddedIndex = state.subtitles.indexOfFirst { it.selected }.takeIf { state.externalSubtitle == null } ?: -1
    val externalIndex = state.addonSubtitles.indexOfFirst { it.subtitle.url == state.externalSubtitle }
    val action = if (state.subtitlesOff) 0 else 1
    val current = embeddedIndex < 0 && externalIndex < 0
    val initialFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            val listHeight = (maxHeight - 145.dp).coerceIn(60.dp, 315.dp)
            ThemedPanel(Modifier.fillMaxWidth()) {
                LaunchedEffect(Unit) { initialFocus.requestFocus() }
                Text("Subtitrări", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListItem(
                        selected = current && action == 0,
                        modifier =
                            if (current && action == 0) {
                                Modifier.weight(1f).focusRequester(initialFocus)
                            } else {
                                Modifier.weight(1f)
                            },
                        onClick = {
                            viewModel.disableSubtitles()
                            onDismiss()
                        },
                        headlineContent = { Text("Oprite") },
                    )
                    ListItem(
                        selected = current && action == 1,
                        modifier =
                            if (current && action == 1) {
                                Modifier.weight(1f).focusRequester(initialFocus)
                            } else {
                                Modifier.weight(1f)
                            },
                        onClick = {
                            viewModel.automaticSubtitles()
                            onDismiss()
                        },
                        headlineContent = { Text("Automat") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrackColumn(
                        title = "Interne",
                        labels = state.subtitles.map { it.label },
                        selected = embeddedIndex,
                        emptyMessage = "Sursa nu are subtitrări detectate.",
                        focus = initialFocus,
                        height = listHeight,
                        modifier = Modifier.weight(1f),
                    ) {
                        viewModel.selectTrack(state.subtitles[it])
                        onDismiss()
                    }
                    TrackColumn(
                        title = "Externe",
                        labels = state.addonSubtitles.map { it.label },
                        selected = externalIndex,
                        emptyMessage = state.addonStatus ?: "Nu am găsit subtitrări externe.",
                        focus = initialFocus,
                        height = listHeight,
                        modifier = Modifier.weight(1f),
                    ) {
                        viewModel.selectAddonSubtitle(state.addonSubtitles[it])
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackColumn(
    title: String,
    labels: List<String>,
    selected: Int,
    emptyMessage: String,
    focus: FocusRequester,
    height: Dp,
    modifier: Modifier,
    onPick: (Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (labels.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = height),
                state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceAtLeast(0)),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(labels) { index, label ->
                    ListItem(
                        selected = selected == index,
                        onClick = { onPick(index) },
                        headlineContent = { Text(label, maxLines = 2) },
                        modifier = if (selected == index) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}
