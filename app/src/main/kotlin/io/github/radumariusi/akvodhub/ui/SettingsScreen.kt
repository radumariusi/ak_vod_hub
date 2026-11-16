package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.update.UpdateState
import io.github.radumariusi.akvodhub.update.UpdateViewModel

@Composable
fun SettingsScreen(
    viewModel: UpdateViewModel,
    state: UpdateState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 720.dp)) {
        Text("Setări", style = MaterialTheme.typography.headlineMedium)

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

        Text("Playlist-uri", style = MaterialTheme.typography.titleLarge)
        Text(
            "Adăugarea de playlist-uri (M3U, Xtream) vine în etapa următoare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Despre", style = MaterialTheme.typography.titleLarge)
        Text(
            "AK VOD Hub, software liber sub licența GPL-3.0. Cod sursă: github.com/radumariusi/ak_vod_hub",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
