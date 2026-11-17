package io.github.radumariusi.akvodhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.remote.PhoneSetupState
import io.github.radumariusi.akvodhub.remote.PhoneSetupViewModel
import io.nayuki.qrcodegen.QrCode

/**
 * Configurare de pe telefon: cod QR cu adresa paginii locale (aceeași rețea), unde se lipesc linkuri, conturi,
 * cheia TMDB și addon-uri, fără tastat cu telecomanda. Serverul se oprește când ieși de pe ecran.
 */
@Composable
fun PhoneSetupScreen(
    viewModel: PhoneSetupViewModel,
    onClose: () -> Unit,
) {
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    BackHandler(onBack = onClose)
    val state by viewModel.state.collectAsState()
    val events by viewModel.events.collectAsState()
    val focus = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 980.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Configurare de pe telefon", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            PhoneSetupState.Starting -> {
                Text("Pornesc pagina de configurare…", style = MaterialTheme.typography.bodyLarge)
            }

            PhoneSetupState.NoNetwork -> {
                Text(
                    "TV-ul nu pare conectat la o rețea locală (Wi-Fi sau cablu). Conectează-l și deschide din nou.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is PhoneSetupState.Ready -> {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    QrImage(s.address.url, Modifier.size(280.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Step("1. Conectează telefonul la aceeași rețea ca TV-ul.")
                        Step("2. Scanează codul cu camera telefonului.")
                        Step("3. Sau deschide în browser adresa:")
                        Text(
                            s.address.url,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Step(
                            "Pe pagină poți adăuga playlist-uri M3U, conturi Xtream, cheia TMDB și addon-uri " +
                                "Stremio, și poți descărca sau restaura backup-ul setărilor.",
                        )
                        if (events.isNotEmpty()) {
                            Text("De pe telefon:", style = MaterialTheme.typography.titleSmall)
                            events.forEach { Step("• $it") }
                        }
                    }
                }
            }
        }
        Button(onClick = onClose, modifier = Modifier.focusRequester(focus)) { Text("Gata") }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Step(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Codul QR desenat direct (alb-negru, cu margine albă, ca să-l citească orice cameră). */
@Composable
private fun QrImage(
    text: String,
    modifier: Modifier,
) {
    val qr = remember(text) { QrCode.encodeText(text, QrCode.Ecc.MEDIUM) }
    Box(modifier.background(Color.White).padding(16.dp)) {
        Canvas(Modifier.size(248.dp)) {
            val cell = size.minDimension / qr.size
            for (y in 0 until qr.size) {
                for (x in 0 until qr.size) {
                    if (qr.getModule(x, y)) {
                        drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}
