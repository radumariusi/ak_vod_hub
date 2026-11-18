package io.github.radumariusi.akvodhub.ui.hub

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import kotlinx.coroutines.delay
import coil3.Image as CoilImage

/** Textul rămâne vizibil până când logo-ul chiar s-a încărcat, inclusiv offline. */
@Composable
fun TitleHeading(name: String, logoPaths: List<String>, height: Dp = 76.dp) {
    var candidate by remember(name, logoPaths) { mutableIntStateOf(0) }
    val index = candidate
    val path = logoPaths.getOrNull(index)
    var loaded by remember(name, path) { mutableStateOf(false) }
    LaunchedEffect(name, path, loaded) {
        if (path != null && !loaded) {
            delay(2_000)
            if (candidate == index) candidate = index + 1
        }
    }
    Box(Modifier.widthIn(max = 360.dp).height(height), contentAlignment = Alignment.CenterStart) {
        if (!loaded) {
            TitleHeadingText(name)
        }
        if (path != null) {
            key(name, path) {
                val context = LocalContext.current
                val model = remember(context, path) { logoRequest(context, path).build() }
                AsyncImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onSuccess = { loaded = true },
                    onError = { if (candidate == index) candidate = index + 1 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Folosește imaginea deja decodată, disponibilă chiar la prima desenare. */
@Composable
internal fun HeroHeading(name: String, logo: CoilImage?, height: Dp) {
    Box(
        Modifier.widthIn(max = 360.dp).fillMaxWidth().height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (logo == null) {
            TitleHeadingText(name)
        } else {
            val context = LocalContext.current
            Image(
                painter = remember(context, logo) { logo.asPainter(context) },
                contentDescription = name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TitleHeadingText(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun logoRequest(context: Context, path: String, rasterSize: String = "w500"): ImageRequest.Builder =
    ImageRequest
        .Builder(context)
        .data(Images.logo(path, rasterSize))
        .apply {
            if (path.endsWith(".svg", ignoreCase = true)) {
                decoderFactory(SvgDecoder.Factory(density = SvgDecoder.NO_DENSITY))
            }
        }
