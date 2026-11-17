package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun PosterBadgesSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Insigne pe postere", style = MaterialTheme.typography.titleLarge)
        Text(
            "Alege ce apare pe postere. „Nou” înseamnă adăugat în ultimele șapte zile.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PosterBadge.entries.chunked(2).forEach { row ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge ->
                    OutlinedButton(onClick = { viewModel.togglePosterBadge(badge) }) {
                        Text("${badge.label}: " + if (badge in prefs.posterBadges) "da" else "nu")
                    }
                }
            }
        }
    }
}
