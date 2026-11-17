@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.Button
import kotlinx.coroutines.delay

@Composable
fun TechnicalInfoDialog(viewModel: PlayerViewModel, onClose: () -> Unit) {
    var info by remember { mutableStateOf(viewModel.technicalInfo()) }
    LaunchedEffect(viewModel) {
        while (true) {
            info = viewModel.technicalInfo()
            delay(1_000)
        }
    }
    Dialog(onDismissRequest = onClose) {
        ThemedPanel(Modifier.width(520.dp)) {
            Text("Info tehnic", style = MaterialTheme.typography.titleLarge)
            Text(
                info,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClose) { Text("Închide") }
        }
    }
}
