package io.github.radumariusi.akvodhub.ui.hub

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.R
import java.util.Locale

/**
 * Căutare vocală în aplicație: deschide recunoașterea vocală a TV-ului (Google) și pune textul în căutare.
 * Butonul nu apare dacă TV-ul nu are recunoaștere vocală. Butonul de microfon al telecomenzii folosește
 * căutarea globală Android TV, care ajunge tot aici (vezi `SearchProvider`).
 */
@Composable
fun VoiceSearchButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val intent =
        remember {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Spune titlul filmului sau serialului")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    val available = remember { intent.resolveActivity(context.packageManager) != null }
    if (!available) return
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onText)
        }
    OutlinedButton(onClick = { runCatching { launcher.launch(intent) } }) {
        Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Caută vocal")
    }
}
