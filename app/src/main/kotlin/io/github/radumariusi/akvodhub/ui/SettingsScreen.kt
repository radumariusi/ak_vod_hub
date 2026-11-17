package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.update.UpdateState
import io.github.radumariusi.akvodhub.update.UpdateViewModel

@Composable
fun SettingsScreen(
    viewModel: UpdateViewModel,
    state: UpdateState,
    playlistsViewModel: PlaylistsViewModel,
    tmdbViewModel: TmdbViewModel,
    playbackViewModel: PlaybackSettingsViewModel,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val playlists by playlistsViewModel.playlists.collectAsState()
    val addState by playlistsViewModel.addState.collectAsState()
    val matchStatus by tmdbViewModel.status.collectAsState()
    val keyState by tmdbViewModel.keyState.collectAsState()

    if (adding) {
        AddPlaylistScreen(
            state = addState,
            onAddUrl = playlistsViewModel::addM3uUrl,
            onAddFile = playlistsViewModel::addM3uFile,
            onAddXtream = playlistsViewModel::addXtream,
            onClose = {
                playlistsViewModel.resetAddState()
                adding = false
            },
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Setări", style = MaterialTheme.typography.headlineMedium)

        PlaylistsSection(
            playlists = playlists,
            onAdd = {
                playlistsViewModel.resetAddState()
                adding = true
            },
            onRefresh = playlistsViewModel::refresh,
            onDelete = playlistsViewModel::delete,
        )

        TmdbSection(
            status = matchStatus,
            keyState = keyState,
            onSaveKey = tmdbViewModel::saveKey,
            onClearKey = tmdbViewModel::clearKey,
            onMatchNow = tmdbViewModel::matchNow,
            onRetryUnmatched = tmdbViewModel::retryUnmatched,
        )

        PlaybackSection(playbackViewModel)

        Text("Actualizări", style = MaterialTheme.typography.titleLarge)
        Text("Versiunea instalată: ${viewModel.currentVersion}", style = MaterialTheme.typography.bodyLarge)
        UpdateStatusText(state)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.check() }) { Text("Caută actualizări") }
            val update = state.offeredUpdate()
            if (update != null) {
                Button(onClick = { viewModel.downloadAndInstall(update) }) { Text("Instalează ${update.version}") }
            }
        }

        Text("Despre", style = MaterialTheme.typography.titleLarge)
        Text(
            "AK VOD Hub, software liber sub licența GPL-3.0. Cod sursă: github.com/radumariusi/ak_vod_hub",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
