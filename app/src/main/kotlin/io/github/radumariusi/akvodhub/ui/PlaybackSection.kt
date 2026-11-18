package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import io.github.radumariusi.akvodhub.parser.player.SourceRule
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import java.util.Locale

/**
 * Setări → Redare. Profilul alege singur sursa (ex. „4K EN, altfel 1080p RO”); dacă sursa nu merge,
 * playerul trece la următoarea din aceeași ordine.
 */
@Composable
fun PlaybackSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    val languages by viewModel.languages.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    val rules = prefs.profile.rules
    val qualities = listOf<VideoQuality?>(null) + VideoQuality.entries.reversed()
    val languageOptions =
        (
            listOf(
                null,
                "en",
                "ro",
                ParsedTitle.MULTI,
            ) + languages + rules.map { it.language }
        ).distinct()

    fun audioLabel(code: String?): String = if (code == ParsedTitle.MULTI) "MULTI" else languageName(code)

    fun pickQuality(
        index: Int,
        current: SourceRule,
    ) {
        choice =
            Choice("Calitate", qualities.map { qualityName(it) }, qualities.indexOf(current.quality)) { i ->
                viewModel.setRule(index, current.copy(quality = qualities[i]))
            }
    }

    fun pickLanguage(
        index: Int,
        current: SourceRule,
    ) {
        choice =
            Choice("Limbă audio", languageOptions.map(::audioLabel), languageOptions.indexOf(current.language)) {
                viewModel.setRule(index, current.copy(language = languageOptions[it]))
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Redare", style = MaterialTheme.typography.titleLarge)
        Text("Ordinea surselor", style = MaterialTheme.typography.titleMedium)
        Text(
            if (rules.isEmpty()) {
                "Automat · 4K EN, RO sau MULTI, apoi surse EN, RO sau MULTI la alte rezoluții."
            } else {
                "Reguli personalizate · ${prefs.profile.describe()}."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Dacă redarea se întrerupe repetat, playerul încearcă o variantă mai ușoară de la aceeași poziție. " +
                "Sursa aleasă manual pornește prima. MULTI este o etichetă din playlist. Pistele reale apar în player.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (prefs.preferOriginalLanguage) {
            Text("„Preferă limba originală” este activă și are prioritate în modul automat.")
        }
        rules.forEachIndexed { index, rule ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (index == 0) "Prima alegere" else "Apoi", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { pickQuality(index, rule) }) { Text(qualityName(rule.quality)) }
                OutlinedButton(onClick = { pickLanguage(index, rule) }) { Text(audioLabel(rule.language)) }
                OutlinedButton(onClick = { viewModel.setRule(index, null) }) { Text("Șterge regula") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rules.size < SourceProfile.MAX_RULES) {
                OutlinedButton(onClick = {
                    viewModel.loadLanguages()
                    viewModel.setRule(rules.size, SourceRule(VideoQuality.FHD, null))
                }) { Text(if (rules.isEmpty()) "Adaugă o regulă personalizată" else "Adaugă o regulă de rezervă") }
            }
        }
        OutlinedButton(onClick = {
            val options = io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults.speeds
            choice =
                Choice("Viteză implicită", options.map { "$it×" }, options.indexOf(prefs.defaultSpeed)) {
                    viewModel.setDefaultSpeed(options[it])
                }
        }) { Text("Viteză implicită: ${prefs.defaultSpeed}×") }
        Text("La deschiderea playerului. Viteza din player rămâne valabilă pentru sesiunea curentă.")
        OutlinedButton(onClick = { viewModel.setAutoNext(!prefs.autoNext) }) {
            Text("Episodul următor automat: " + if (prefs.autoNext) "da" else "nu")
        }
        Text("Pornește după numărătoarea de zece secunde; timerul de somn are prioritate.")
        OutlinedButton(onClick = { viewModel.setContinueDirect(!prefs.continueDirect) }) {
            Text("OK în Continuă vizionarea · " + if (prefs.continueDirect) "Redă direct" else "Deschide acțiunile")
        }
        OutlinedButton(onClick = {
            val options = PlaybackSettings.CONTROLS_SECONDS
            choice =
                Choice("Durata comenzilor", options.map { "$it secunde" }, options.indexOf(prefs.controlsSeconds)) {
                    viewModel.setControlsSeconds(options[it])
                }
        }) { Text("Durata comenzilor · ${prefs.controlsSeconds} secunde") }
        Text("Ambele preferințe se aplică imediat.")
        Text("Avansate", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { viewModel.setMatchFrameRate(!prefs.matchFrameRate) }) {
            Text("Potrivește frecvența ecranului: " + if (prefs.matchFrameRate) "da" else "nu")
        }
        Text("Păstrează rezoluția; unele TV-uri clipesc la schimbarea frecvenței.")
        OutlinedButton(onClick = {
            val options = io.github.radumariusi.akvodhub.parser.player.BufferPreset.entries
            choice =
                Choice("Buffer", options.map { it.label }, options.indexOf(prefs.bufferPreset)) {
                    viewModel.setBufferPreset(options[it])
                }
        }) { Text("Buffer: ${prefs.bufferPreset.label}") }
        Text("Se aplică la următoarea deschidere a playerului. Mare păstrează o rezervă mai mare în memorie.")
        Text(
            "Decodor audio software (AC3, EAC3, DTS): ${viewModel.ffmpegStatus}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}

private fun qualityName(quality: VideoQuality?) =
    if (quality == null) "Orice calitate" else SourceGrouping.qualityLabel(quality)

internal fun languageName(code: String?): String {
    if (code == null) return "Orice limbă"
    val ro = Locale.forLanguageTag("ro")
    val name = Locale.forLanguageTag(code).getDisplayLanguage(ro).replaceFirstChar { it.titlecase(ro) }
    return if (name.isBlank() ||
        name.equals(code, ignoreCase = true)
    ) {
        code.uppercase()
    } else {
        "$name (${code.uppercase()})"
    }
}
