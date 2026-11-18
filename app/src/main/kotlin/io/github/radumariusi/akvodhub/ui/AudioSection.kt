package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun AudioSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = {
            val options = listOf("") + viewModel.subtitleLanguageOptions
            choice =
                Choice(
                    "Limbă audio preferată",
                    options.map {
                        if (it.isEmpty()) "Din profil și conținut" else languageName(it)
                    },
                    options.indexOf(prefs.audioLanguage),
                ) {
                    viewModel.setContent("audio_language", options[it])
                }
        }) {
            Text(
                "Limbă audio: " +
                    prefs.audioLanguage
                        .takeIf { it.isNotEmpty() }
                        ?.let(
                            ::languageName,
                        ).orEmpty()
                        .ifEmpty { "Din profil și conținut" },
            )
        }
        Text(
            "Preferința memorată per serial și opțiunea pentru original au prioritate. Dacă pista lipsește, se folosesc limbile globale.",
        )
        Text("Limba preferată se aplică la următoarea redare.")
        Text("Avansate", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { viewModel.setAudioPassthrough(!prefs.audioPassthrough) }) {
            Text("Passthrough audio: " + if (prefs.audioPassthrough) "da" else "nu")
        }
        Text(
            "Trimite AC3/E-AC3/DTS către ieșirea compatibilă; altfel decodare PCM. Se aplică la redeschiderea playerului.",
        )
        OutlinedButton(onClick = { viewModel.setNightMode(!prefs.nightMode) }) {
            Text("Mod noapte / dialog: " + if (prefs.nightMode) "da" else "nu")
        }
        Text("Reduce diferențele de volum în PCM și suspendă passthrough. Se aplică la redeschiderea playerului.")
        Text("Decodor audio software: ${viewModel.ffmpegStatus}")
    }
    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}
