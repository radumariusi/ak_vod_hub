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
import io.github.radumariusi.akvodhub.parser.title.CategoryMode
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
        Text("În modul automat, prioritizează sursele care declară limba originală. Se aplică la următoarea redare.")
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
        CategoryRulesList(viewModel)
    }
}

/** Limba pe categorii: Automat → Audio original (limba categoriei devine subtitrare) → Dublat. */
@Composable
private fun CategoryRulesList(viewModel: PlaybackSettingsViewModel) {
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.categoryRules.collectAsState()
    Text("Limba audio pe categorii", style = MaterialTheme.typography.titleLarge)
    Text(
        "Dacă o categorie are limba detectată greșit, alege: Automat, Audio original (categoria = subtitrare) " +
            "sau Dublat (categoria = limba audio). Sursele se reanalizează în fundal.",
    )
    val list = categories
    if (list == null) {
        OutlinedButton(onClick = viewModel::loadCategories) { Text("Arată categoriile") }
        return
    }
    if (list.isEmpty()) Text("Nicio categorie în playlist-uri.")
    list.forEach { category ->
        val mode = rules[category.groupTitle.trim()] ?: CategoryMode.AUTO
        OutlinedButton(onClick = { viewModel.cycleCategoryRule(category.groupTitle) }) {
            Text("${category.groupTitle.trim()} (${category.total}) · ${modeLabel(mode)}")
        }
    }
}

private fun modeLabel(mode: CategoryMode) =
    when (mode) {
        CategoryMode.AUTO -> "Automat"
        CategoryMode.ORIGINAL -> "Audio original"
        CategoryMode.DUBBED -> "Dublat"
    }

@Composable
private fun ContentText(label: String, current: String, key: String, viewModel: PlaybackSettingsViewModel) {
    var draft by remember(current) { mutableStateOf(current) }
    TvTextField(value = draft, onValueChange = { draft = it }, label = label)
    OutlinedButton(onClick = { viewModel.setContent(key, draft) }, enabled = draft != current) { Text("Salvează") }
}
