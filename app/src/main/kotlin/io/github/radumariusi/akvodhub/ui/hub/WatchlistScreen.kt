package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.radumariusi.akvodhub.hub.WatchlistViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onOpen: (TitleCard, String) -> Unit,
    restoreKey: String?,
    onRestored: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val grid = rememberLazyGridState()
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(grid) {
        snapshotFlow {
            val visible = grid.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: -1) to (visible.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisible(first, last) }
    }
    LaunchedEffect(restoreKey, state.items) {
        val id = restoreKey?.substringAfterLast(':')?.toLongOrNull() ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.id == id }
        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == id }) grid.scrollToItem(index)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lista mea", style = MaterialTheme.typography.headlineMedium)
        if (state.items.isEmpty()) {
            Message(
                if (state.loading) {
                    "Se încarcă…"
                } else {
                    "Lista mea este goală. Adaugă titluri din fișa filmului sau serialului; " +
                        "aici apar cele disponibile în playlist."
                },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(PosterWidth + 16.dp),
            state = grid,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(10.dp),
        ) {
            itemsIndexed(state.items, key = { _, card -> card.id }) { _, card ->
                val key = "watchlist:${card.id}"
                PosterCard(
                    card,
                    onClick = { onOpen(card, key) },
                    onFocused = {},
                    requestFocus =
                        if (restoreKey != null) {
                            key == restoreKey
                        } else {
                            !initialFocusDone && card.id == state.items.firstOrNull()?.id
                        },
                    onFocusRequested = {
                        initialFocusDone = true
                        onRestored()
                    },
                )
            }
        }
    }
}
