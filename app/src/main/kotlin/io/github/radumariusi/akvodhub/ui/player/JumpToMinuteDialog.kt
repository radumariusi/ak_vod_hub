@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun JumpToMinuteDialog(viewModel: PlayerViewModel, onClose: () -> Unit) {
    var minute by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        ThemedPanel(Modifier.width(520.dp)) {
            val colors = MaterialTheme.colorScheme
            Text("Salt la minut (de la început)", style = MaterialTheme.typography.titleLarge)
            BasicTextField(
                value = minute,
                onValueChange = { minute = it.filter { c -> c in '0'..'9' }.take(8) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = colors.onSurfaceVariant),
                cursorBrush = SolidColor(colors.onSurfaceVariant),
                singleLine = true,
                modifier =
                    Modifier.fillMaxWidth().background(colors.surfaceVariant, MaterialTheme.shapes.small).padding(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (viewModel.jumpToMinute(minute)) onClose() }) { Text("Mergi") }
                OutlinedButton(onClick = onClose) { Text("Anulează") }
            }
        }
    }
}
