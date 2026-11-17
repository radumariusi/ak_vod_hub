package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.player.SubtitleStyle
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.player.AddonAddState
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

/**
 * Setări → Subtitrări: limbile preferate (ex. RO, apoi EN), pornirea automată din addon-uri, mărimea, stilul
 * și addon-urile Stremio (implicit OpenSubtitles v3).
 */
@Composable
fun SubtitlesSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    val colors = MaterialTheme.colorScheme
    val languages = prefs.subtitleLanguages
    val options = viewModel.subtitleLanguageOptions


    fun pickLanguage(index: Int) {
        val current = languages.getOrNull(index)
        val list = listOf<String?>(null) + options.filter { it == current || it !in languages }
        choice =
            Choice(
                if (index == 0) "Prima limbă" else "Limba ${index + 1}",
                list.map { it?.let(::languageName) ?: if (index == 0) "Oprite" else "Niciuna" },
                list.indexOf(current).takeIf { it >= 0 },
            ) { viewModel.setSubtitleLanguage(index, list[it]) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Subtitrări", style = MaterialTheme.typography.titleLarge)
        Text(
            if (languages.isEmpty()) {
                "Subtitrările sunt oprite. Le poți porni oricând din player."
            } else {
                "Limbi preferate: " + languages.joinToString(", apoi ") { languageName(it) } +
                    ". Playerul alege subtitrarea din sursă dacă are una în limba preferată; altfel o caută în " +
                    "addon-uri."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            languages.forEachIndexed { index, code ->
                OutlinedButton(onClick = { pickLanguage(index) }) {
                    Text("${index + 1}. ${languageName(code)}")
                }
            }
            if (languages.size < PlaybackSettings.MAX_SUBTITLE_LANGUAGES) {
                OutlinedButton(onClick = { pickLanguage(languages.size) }) {
                    Text(if (languages.isEmpty()) "Pornește subtitrările" else "Adaugă „apoi”")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.setAddonSubtitlesAuto(!prefs.addonSubtitlesAuto) }) {
                Text("Automat din addon-uri: ${if (prefs.addonSubtitlesAuto) "da" else "nu"}")
            }
            OutlinedButton(onClick = {
                val scales = PlaybackSettings.SCALES
                choice =
                    Choice("Mărime", scales.map { "$it%" }, scales.indexOf(prefs.subtitleScale).takeIf { it >= 0 }) {
                        viewModel.setSubtitleScale(scales[it])
                    }
            }) { Text("Mărime: ${prefs.subtitleScale}%") }
            OutlinedButton(onClick = {
                val styles = SubtitleStyle.entries
                choice =
                    Choice("Stil", styles.map { it.label }, styles.indexOf(prefs.subtitleStyle)) {
                        viewModel.setSubtitleStyle(styles[it])
                    }
            }) { Text("Stil: ${prefs.subtitleStyle.label}") }
        }

        OutlinedButton(onClick = {
            val offsets = (-20..20).map { it * 100L }
            choice = Choice("Întârziere implicită addon", offsets.map { "$it ms" }, offsets.indexOf(prefs.subtitleDelayMs).takeIf { it >= 0 }) {
                viewModel.setSubtitleDelayMs(offsets[it])
            }
        }) { Text("Întârziere implicită: ${prefs.subtitleDelayMs} ms") }
        Text("Pentru subtitrările din addon-uri, la fiecare film/episod. Pozitiv = subtitrarea apare mai târziu. Ajustarea din player rămâne pe episod.")

    }

    choice?.let { ChoiceDialog(it, onDismiss = { choice = null }) }
}

@Composable
fun StremioSection(viewModel: PlaybackSettingsViewModel) {
    val addons by viewModel.addons.collectAsState()
    val addState by viewModel.addState.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(addState) { if (addState is AddonAddState.Added) url = "" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Addon-uri Stremio", style = MaterialTheme.typography.titleMedium)
        Text(
            "Folosite pentru subtitrări, cataloage și liste (top-uri, MDBList, Trakt). Cataloagele apar ca rânduri " +
                "pe Acasă, doar cu titlurile care există în playlist-uri.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (addons.isEmpty()) {
                Text("Niciun addon: subtitrările vin doar din surse.", style = MaterialTheme.typography.bodyMedium)
            }
            addons.forEachIndexed { index, addon ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(addon.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            addonRoles(addon.resources) +
                                addon.manifestUrl.removePrefix("https://").removeSuffix("/manifest.json"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index > 0) {
                        OutlinedButton(onClick = { viewModel.moveAddonUp(addon.manifestUrl) }) { Text("Sus") }
                    }
                    OutlinedButton(onClick = { viewModel.removeAddon(addon.manifestUrl) }) { Text("Șterge") }
                }
            }
            if (addons.none { it.manifestUrl == StremioAddons.OPENSUBTITLES.manifestUrl }) {
                OutlinedButton(onClick = viewModel::addOpenSubtitles) { Text("Adaugă OpenSubtitles v3") }
            }
            TvTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (addState is AddonAddState.Error) viewModel.resetAddState()
                },
                label = "Adresa altui addon (manifest.json)",
                placeholder = "https://exemplu.strem.io/manifest.json",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            )
            when (val st = addState) {
                is AddonAddState.Error -> {
                    Text(st.message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }

                is AddonAddState.Added -> {
                    Text("Am adăugat „${st.name}”.", style = MaterialTheme.typography.bodyMedium)
                }

                else -> {}
            }
            Button(
                onClick = { viewModel.addAddon(url) },
                enabled = url.isNotBlank() && addState != AddonAddState.Checking,
            ) { Text(if (addState == AddonAddState.Checking) "Se verifică…" else "Adaugă addon") }
        }
    }
}

/** Ex. „Subtitrări · Cataloage · ” (gol dacă nu știm ce oferă addon-ul). */
private fun addonRoles(resources: List<String>): String {
    val roles =
        listOfNotNull(
            "Subtitrări".takeIf { "subtitles" in resources },
            "Cataloage".takeIf { "catalog" in resources },
        )
    return if (roles.isEmpty()) "" else roles.joinToString(" · ", postfix = " · ")
}
