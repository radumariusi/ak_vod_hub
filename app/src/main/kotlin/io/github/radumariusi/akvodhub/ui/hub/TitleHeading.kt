package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder

/** Textul rămâne vizibil până când logo-ul chiar s-a încărcat, inclusiv offline. */
@Composable
fun TitleHeading(name: String, logoPaths: List<String>, height: Dp = 76.dp) {
    var candidate by remember(name, logoPaths) { mutableIntStateOf(0) }
    val path = logoPaths.getOrNull(candidate)
    var loaded by remember(name, path) { mutableStateOf(false) }
    Box(Modifier.widthIn(max = 360.dp).height(height), contentAlignment = Alignment.CenterStart) {
        if (!loaded) {
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (path != null) {
            key(name, path) {
                val context = LocalContext.current
                val model =
                    remember(context, path) {
                        ImageRequest
                            .Builder(context)
                            .data(Images.logo(path))
                            .apply {
                                if (path.endsWith(".svg", ignoreCase = true)) {
                                    decoderFactory(SvgDecoder.Factory(density = SvgDecoder.NO_DENSITY))
                                }
                            }.build()
                    }
                AsyncImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onSuccess = { loaded = true },
                    onError = { candidate++ },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
