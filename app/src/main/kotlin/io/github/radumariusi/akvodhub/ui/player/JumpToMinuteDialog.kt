@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
fun JumpToMinuteDialog(viewModel: PlayerViewModel, onClose: () -> Unit) {
    var minute by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(Color(0xFF202020)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Salt la minut (de la început)")
            BasicTextField(
                value = minute,
                onValueChange = { minute = it.filter { c -> c in '0'..'9' }.take(8) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Color.White),
                singleLine = true,
                modifier = Modifier.background(Color.DarkGray).padding(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (viewModel.jumpToMinute(minute)) onClose() }) { Text("Mergi") }
                OutlinedButton(onClick = onClose) { Text("Anulează") }
            }
        }
    }
}
