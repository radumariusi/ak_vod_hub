package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.playlist.LibraryTotals

@Composable
fun HomeScreen(
    totals: LibraryTotals?,
    onOpenSettings: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("AK VOD Hub", style = MaterialTheme.typography.displaySmall)
        val hasPlaylists = totals != null && totals.playlists > 0
        Text(
            if (totals != null && hasPlaylists) {
                "Biblioteca ta: ${formatCount(totals.movies)} filme, ${formatCount(totals.series)} seriale" +
                    (if (totals.episodes > 0) " și ${formatCount(totals.episodes)} episoade" else "") +
                    ", din ${totals.playlists} playlist-uri. Hub-ul cu postere și căutare vine în etapele următoare."
            } else {
                "Încă nu ai niciun playlist. Adaugă din Setări un cont Xtream sau un playlist M3U; " +
                    "păstrăm doar filmele și serialele."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Button(onClick = onOpenSettings, modifier = Modifier.focusRequester(focus)) {
            Text(if (hasPlaylists) "Playlist-uri" else "Adaugă un playlist")
        }
    }
}

@Composable
fun SearchScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Căutare", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Căutarea în filmele și serialele din playlist vine într-o etapă următoare.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
