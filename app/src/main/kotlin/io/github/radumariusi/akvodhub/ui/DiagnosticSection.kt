package io.github.radumariusi.akvodhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

/** Setări → Avansate: câte surse nu au limbă sau calitate declarată, și unde se descarcă exportul complet. */
@Composable
fun DiagnosticSection(viewModel: PlaybackSettingsViewModel) {
    val text by viewModel.diagnostic.collectAsState()
    OutlinedButton(onClick = viewModel::loadDiagnostic) { Text("Diagnostic surse") }
    text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text(
        "Exportul complet (categorii, nume, metadate Xtream, fără parole): Configurare de pe telefon → " +
            "Descarcă diagnostic.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
