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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.remote.PhoneSetupViewModel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import io.github.radumariusi.akvodhub.update.UpdateState
import io.github.radumariusi.akvodhub.update.UpdateViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: UpdateViewModel,
    state: UpdateState,
    playlistsViewModel: PlaylistsViewModel,
    tmdbViewModel: TmdbViewModel,
    playbackViewModel: PlaybackSettingsViewModel,
    phoneSetupViewModel: PhoneSetupViewModel,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var phoneSetup by rememberSaveable { mutableStateOf(false) }
    val playlists by playlistsViewModel.playlists.collectAsState()
    val addState by playlistsViewModel.addState.collectAsState()
    val matchStatus by tmdbViewModel.status.collectAsState()
    val keyState by tmdbViewModel.keyState.collectAsState()
    val imdb by tmdbViewModel.imdb.collectAsState()

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

    if (phoneSetup) {
        PhoneSetupScreen(phoneSetupViewModel, onClose = { phoneSetup = false })
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Setări", style = MaterialTheme.typography.headlineMedium)

        Text("Configurare de pe telefon", style = MaterialTheme.typography.titleLarge)
        Text(
            "Scanezi un cod QR și lipești de pe telefon linkuri M3U, conturi Xtream, cheia TMDB sau addon-uri, " +
                "fără tastat cu telecomanda. Tot acolo: backup și restaurare setări.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { phoneSetup = true }) { Text("Configurează de pe telefon") }

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

        Text("Note IMDb", style = MaterialTheme.typography.titleLarge)
        Text(
            "Din fișierul public oficial IMDb, actualizat la șapte zile; fără cheie API. " +
                "Se păstrează numai notele titlurilor cu ID IMDb din playlist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val updated =
            imdb.lastUpdated?.let {
                DateFormat
                    .getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.forLanguageTag("ro"),
                    ).format(Date(it))
            }
        Text(
            if (updated ==
                null
            ) {
                "Notele IMDb nu au fost încă actualizate."
            } else {
                "Ultima actualizare: $updated · ${imdb.updatedTitles} titluri"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        imdb.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (imdb.running) {
            Text(
                "Actualizare în curs sau programată; se amână în timpul redării.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = tmdbViewModel::refreshImdb, enabled = !imdb.running) { Text("Actualizează acum") }

        PosterBadgesSection(playbackViewModel)

        PlaybackSection(playbackViewModel)

        SubtitlesSection(playbackViewModel)

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
