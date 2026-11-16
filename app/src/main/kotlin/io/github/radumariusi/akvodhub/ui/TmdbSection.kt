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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.catalog.KeyState
import io.github.radumariusi.akvodhub.data.catalog.MatchStatus

/** Setări → TMDB: cheia utilizatorului și progresul potrivirii titlurilor. */
@Composable
fun TmdbSection(
    status: MatchStatus?,
    keyState: KeyState,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onMatchNow: () -> Unit,
    onRetryUnmatched: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var editing by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf("") }
    val hasKey = status?.hasKey == true

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TMDB (postere, descrieri, unirea duplicatelor)", style = MaterialTheme.typography.titleLarge)
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasKey || editing) {
                Text(
                    "Ai nevoie de o cheie TMDB gratuită: cont pe themoviedb.org → Setări → API → " +
                        "„API Key” (sau „API Read Access Token”). Cheia rămâne doar pe acest dispozitiv.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                TvTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = "Cheie TMDB",
                    imeAction = ImeAction.Done,
                )
                if (keyState is KeyState.Error) {
                    Text(keyState.message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            onSaveKey(key)
                            editing = false
                        },
                        enabled = key.isNotBlank() && keyState != KeyState.Checking,
                    ) { Text(if (keyState == KeyState.Checking) "Se verifică…" else "Salvează cheia") }
                    if (editing) OutlinedButton(onClick = { editing = false }) { Text("Renunță") }
                }
            } else {
                Text("Cheie TMDB salvată.", style = MaterialTheme.typography.bodyLarge)
            }

            if (status != null) {
                Text(statusLine(status), style = MaterialTheme.typography.bodyLarge)
                status.lastError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }

            if (hasKey && !editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onMatchNow, enabled = status?.running != true) { Text("Potrivește acum") }
                    if ((status?.unmatched ?: 0) > 0) {
                        OutlinedButton(onClick = onRetryUnmatched) { Text("Reîncearcă nepotrivitele") }
                    }
                    OutlinedButton(onClick = {
                        key = ""
                        editing = true
                    }) { Text("Schimbă cheia") }
                    OutlinedButton(onClick = onClearKey) { Text("Șterge cheia") }
                }
            }
        }
    }
}

private fun statusLine(s: MatchStatus): String =
    buildString {
        append("${formatCount(s.titles)} filme și seriale unice · ")
        append("${formatCount(s.matched)} intrări potrivite")
        if (s.unmatched > 0) append(" · ${formatCount(s.unmatched)} nepotrivite")
        if (s.pending > 0) append(" · ${formatCount(s.pending)} în așteptare")
        if (s.running) append(" · se potrivește…")
    }
