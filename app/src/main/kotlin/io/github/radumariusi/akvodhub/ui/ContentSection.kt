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
        OutlinedButton(onClick = { viewModel.setHideAdultMovies(!prefs.hideAdultMovies) }) {
            Text("Filme din categorii pentru adulți · " + if (prefs.hideAdultMovies) "Ascunse" else "Afișate")
        }
        Text("Se aplică la reindexare, după etichetele categoriilor din playlist (Adulți, XXX, 18+).")
        Text("Avansate", style = MaterialTheme.typography.titleLarge)
        ContentText("Limbi ascunse (coduri)", prefs.hiddenLanguages, "hidden_languages", viewModel)
        ContentText(
            "Categorii ascunse (nume exacte, separate prin virgulă)",
            prefs.hiddenCategories,
            "hidden_categories",
            viewModel,
        )
        Text(
            "La reindexare, limbile ascund variantele respective. Categoriile ascund titlurile integral.",
        )
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
