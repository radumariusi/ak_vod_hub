package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.parser.catalog.AccentColor
import io.github.radumariusi.akvodhub.parser.catalog.PosterSize
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun AppearanceSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.setHomeHero(!prefs.homeHero) }) {
            Text("Hero pe Acasă: " + if (prefs.homeHero) "da" else "nu")
        }
        Text("Fundalul și descrierea titlului selectat, deasupra rândurilor de pe Acasă.")
        Text("Aspect", style = MaterialTheme.typography.titleLarge)
        Text("Posterele mici lasă loc pentru mai multe titluri pe ecran.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            choice =
                Choice(
                    "Mărime postere",
                    PosterSize.entries.map { it.label },
                    prefs.posterSize.ordinal,
                    onPick = { viewModel.setPosterSize(PosterSize.entries[it]) },
                )
        }) { Text("Mărime postere: ${prefs.posterSize.label}") }
        OutlinedButton(onClick = {
            choice =
                Choice(
                    "Culoare de accent",
                    AccentColor.entries.map { it.label },
                    prefs.accentColor.ordinal,
                    onPick = { viewModel.setAccentColor(AccentColor.entries[it]) },
                )
        }) {
            Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary))
            Text("  Culoare de accent: ${prefs.accentColor.label}")
        }
    }
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}
