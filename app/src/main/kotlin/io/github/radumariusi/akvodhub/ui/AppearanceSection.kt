package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.parser.catalog.AccentColor
import io.github.radumariusi.akvodhub.parser.catalog.HomeRows
import io.github.radumariusi.akvodhub.parser.catalog.PosterSize
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.ui.hub.Choice
import io.github.radumariusi.akvodhub.ui.hub.ChoiceDialog
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun AppearanceSection(viewModel: PlaybackSettingsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    var configureRows by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.setHomeHero(!prefs.homeHero) }) {
            Text("Hero pe Acasă: " + if (prefs.homeHero) "da" else "nu")
        }
        Text("Aspectul și rândurile de pe Acasă se aplică imediat.")
        OutlinedButton(onClick = { configureRows = true }) { Text("Rânduri pe Acasă") }
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
    if (configureRows) {
        HomeRowsDialog(HomeRows.order(prefs.homeOrder), prefs.homeHidden, { configureRows = false }) { order, hidden ->
            viewModel.setHomeRows(order, hidden)
            configureRows = false
        }
    }
}

@Composable
private fun HomeRowsDialog(
    initialOrder: List<String>,
    initialHidden: Set<String>,
    dismiss: () -> Unit,
    apply: (List<String>, Set<String>) -> Unit,
) {
    var order by remember { mutableStateOf(initialOrder) }
    var hidden by remember { mutableStateOf(initialHidden) }

    fun move(from: Int, to: Int) {
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    Dialog(onDismissRequest = dismiss) {
        ThemedPanel {
            Text("Rânduri pe Acasă", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.heightIn(max = 400.dp).weight(1f, fill = false)) {
                itemsIndexed(order, key = { _, key -> key }) { index, key ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { hidden = if (key in hidden) hidden - key else hidden + key }) {
                            Text("${HomeRows.labels[key]} · " + if (key in hidden) "ascuns" else "vizibil")
                        }
                        OutlinedButton(onClick = { move(index, index - 1) }, enabled = index > 0) { Text("Sus") }
                        OutlinedButton(
                            onClick = { move(index, index + 1) },
                            enabled = index < order.lastIndex,
                        ) { Text("Jos") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { apply(order, hidden) }) { Text("Aplică") }
                OutlinedButton(onClick = dismiss) { Text("Anulează") }
            }
        }
    }
}
