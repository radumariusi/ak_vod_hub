package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.playlist.PlaylistSummary
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val roLocale: Locale = Locale.forLanguageTag("ro")

fun formatCount(n: Int): String = String.format(roLocale, "%,d", n)

@Composable
fun PlaylistsSection(
    playlists: List<PlaylistSummary>,
    onAdd: () -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Playlist-uri", style = MaterialTheme.typography.titleLarge)
        if (playlists.isEmpty()) {
            Text(
                "Niciun playlist încă. Adaugă un link M3U, un fișier M3U sau un cont Xtream. " +
                    "Păstrăm doar filmele și serialele; Live TV se ignoră.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        playlists.forEach { playlist ->
            PlaylistCard(playlist, onRefresh = { onRefresh(playlist.id) }, onDelete = { onDelete(playlist.id) })
        }
        Button(onClick = onAdd) { Text("Adaugă playlist") }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistSummary,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .widthIn(max = 720.dp)
                .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val type =
            when (playlist.type) {
                PlaylistType.M3U_URL -> "M3U"
                PlaylistType.M3U_FILE -> "Fișier M3U"
                PlaylistType.XTREAM -> "Xtream"
            }
        Text(playlist.name, style = MaterialTheme.typography.titleMedium)
        Text("$type · ${playlist.label}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(
            buildString {
                append("${formatCount(playlist.movies)} filme")
                if (playlist.type == PlaylistType.XTREAM || playlist.series > 0) {
                    append(" · ${formatCount(playlist.series)} seriale")
                }
                if (playlist.episodes > 0) append(" · ${formatCount(playlist.episodes)} episoade")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(statusText(playlist), style = MaterialTheme.typography.bodyMedium, color = statusColor(playlist))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = playlist.activity == null) { Text("Reîmprospătează") }
            if (confirmDelete) {
                Button(onClick = onDelete) { Text("Confirmă ștergerea") }
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Renunță") }
            } else {
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Șterge") }
            }
        }
    }
}

@Composable
private fun statusColor(playlist: PlaylistSummary) =
    if (playlist.activity == null && playlist.lastImportError != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun statusText(playlist: PlaylistSummary): String {
    val activity = playlist.activity
    val progress = activity?.progress
    val lastImportAt = playlist.lastImportAt
    return when {
        progress != null -> {
            "${progress.phase} ${formatCount(
                progress.scanned,
            )} intrări citite, ${formatCount(progress.kept)} filme/seriale păstrate"
        }

        activity != null -> {
            "Import în așteptare (pornește când e conexiune)…"
        }

        playlist.lastImportError != null -> {
            "Eroare la ultimul import: ${playlist.lastImportError}"
        }

        lastImportAt != null -> {
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", roLocale).format(Date(lastImportAt))
            val seconds = (playlist.lastDurationMs + 500) / 1000
            "Actualizat $date: +${formatCount(playlist.lastAdded)} noi, " +
                "−${formatCount(playlist.lastRemoved)} dispărute, în $seconds s"
        }

        else -> {
            "Încă neimportat."
        }
    }
}
