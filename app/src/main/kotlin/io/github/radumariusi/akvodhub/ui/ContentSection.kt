package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun ContentSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ContentText("Limbi preferate (coduri, în ordine)", prefs.contentLanguages, "content_languages", viewModel)
        Text("Exemplu: ro,en. Pentru surse și piste audio; lista goală păstrează profilul existent.")
        OutlinedButton(onClick = { viewModel.setPreferOriginalLanguage(!prefs.preferOriginalLanguage) }) {
            Text("Preferă limba originală: " + if (prefs.preferOriginalLanguage) "da" else "nu")
        }
        Text("Original înaintea profilului, apoi MULTI. Sursa aleasă manual are prioritate.")
        ContentText("Limbi ascunse (coduri)", prefs.hiddenLanguages, "hidden_languages", viewModel)
        ContentText("Categorii ascunse (nume exacte, separate prin virgulă)", prefs.hiddenCategories, "hidden_categories", viewModel)
        Text("Ascunde titlul întreg dacă o sursă declară o limbă sau categorie blocată. Nu șterge sursele sau istoricul. Lista goală arată tot.")
        OutlinedButton(onClick = { viewModel.togglePosterBadge(PosterBadge.DUB) }) {
            Text("Etichete DUB: " + if (PosterBadge.DUB in prefs.posterBadges) "da" else "nu")
        }
    }
}

@Composable
private fun ContentText(label: String, current: String, key: String, viewModel: PlaybackSettingsViewModel) {
    var draft by remember(current) { mutableStateOf(current) }
    Text("$label: " + current.ifEmpty { "Niciuna" })
    TvTextField(value = draft, onValueChange = { draft = it }, label = label)
    OutlinedButton(onClick = { viewModel.setContent(key, draft) }, enabled = draft != current) { Text("Salvează") }
}
