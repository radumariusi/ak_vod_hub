package io.github.radumariusi.akvodhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageCacheSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Cache-ul imaginilor se reface automat la afișare.") }
    OutlinedButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                message = "Cache-ul imaginilor a fost golit."
            } catch (e: Exception) {
                message = "Cache-ul nu a putut fi golit. Încearcă din nou."
            } finally {
                busy = false
            }
        }
    }) { Text(if (busy) "Se golește cache-ul…" else "Golește cache imagini") }
    Text(message)
}
