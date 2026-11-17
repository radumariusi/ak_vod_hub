package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** Textul rămâne vizibil până când logo-ul chiar s-a încărcat, inclusiv offline. */
@Composable
fun TitleHeading(name: String, logoPath: String?) {
    var loaded by remember(logoPath) { mutableStateOf(false) }
    Box(Modifier.widthIn(max = 300.dp).height(64.dp)) {
        if (!loaded) {
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (logoPath != null) {
            AsyncImage(
                model = Images.logo(logoPath),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
