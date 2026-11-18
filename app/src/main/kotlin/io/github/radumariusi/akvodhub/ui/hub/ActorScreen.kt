package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.ActorState
import io.github.radumariusi.akvodhub.hub.ActorViewModel
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ActorScreen(
    viewModel: ActorViewModel,
    personId: Long,
    name: String,
    onBack: () -> Unit,
    onOpen: (TitleCard) -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val state = snapshot.takeIf { it.personId == personId } ?: ActorState(personId)
    val grid = rememberLazyGridState()
    var opened by rememberSaveable(personId) { mutableStateOf<Long?>(null) }
    var requestFocus by rememberSaveable(personId) { mutableStateOf(true) }
    LaunchedEffect(personId) { viewModel.open(personId, restoreThrough = opened) }
    LaunchedEffect(grid, personId) {
        snapshotFlow {
            val visible = grid.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: -1) to (visible.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisible(first, last) }
    }
    LaunchedEffect(opened, requestFocus, state.items) {
        if (!requestFocus) return@LaunchedEffect
        val index = state.items.indexOfFirst { it.id == opened }
        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == opened }) {
            grid.scrollToItem(index)
        }
    }
    Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = MaterialTheme.typography.headlineMedium)
        Text("Filme și seriale disponibile în playlist", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onBack) { Text("Înapoi la fișă") }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { viewModel.open(personId, true) }) { Text("Încearcă din nou") }
        }
        if (state.loading && state.items.isEmpty()) Text("Se încarcă filmografia…")
        if (!state.loading && state.items.isEmpty() &&
            state.error == null
        ) {
            Text("Nu există titluri disponibile în playlist.")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(PosterWidth + 16.dp),
            state = grid,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(10.dp),
        ) {
            itemsIndexed(state.items, key = { _, card -> card.id }) { _, card ->
                PosterCard(
                    card,
                    onClick = {
                        opened = card.id
                        requestFocus = true
                        onOpen(card)
                    },
                    onFocused = {},
                    requestFocus = requestFocus && card.id == (opened ?: state.items.firstOrNull()?.id),
                    onFocusRequested = { requestFocus = false },
                )
            }
        }
    }
}
