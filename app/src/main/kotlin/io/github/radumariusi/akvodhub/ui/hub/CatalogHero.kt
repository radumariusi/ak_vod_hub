package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import coil3.size.Scale
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
internal fun TitleBackdrop(path: String?, modifier: Modifier = Modifier, opacity: Float = 0.82f) {
    val artwork = Images.hero(path) ?: return
    val background = MaterialTheme.colorScheme.background
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = opacity,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to background,
                    0.40f to background.copy(alpha = 0.78f),
                    0.72f to background.copy(alpha = 0.15f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to background),
            ),
        )
    }
}

@Composable
internal fun catalogHeroHeight(available: Dp): Dp =
    minOf((available * 0.54f).coerceAtMost(264.dp), available - PosterHeight - 64.dp).coerceAtLeast(136.dp)

private data class HeroPresentation(val card: TitleCard, val logo: Image?)

private fun logoKey(titleId: Long) = "hero-logo:$titleId"

/** Începe încărcarea pe focus și afișează direct sigla decodată când este în cache. */
@Composable
internal fun CatalogHero(
    fallback: TitleCard,
    focused: () -> TitleCard?,
    logo: suspend (Long) -> List<String>,
    height: Dp,
    onBackdropChanged: ((String?) -> Unit)? = null,
    neighborTitleIds: () -> List<Long> = { emptyList() },
    onPlay: (TitleCard) -> Unit,
    onDetails: (TitleCard) -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val target = focused() ?: fallback
    val latestTarget by rememberUpdatedState(target)
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }

    fun cached(id: Long): Image? = imageLoader.memoryCache?.get(MemoryCache.Key(logoKey(id)))?.image

    suspend fun load(id: Long): Image? =
        cached(id) ?: withTimeoutOrNull(6_000L) {
            val density = context.resources.displayMetrics.density
            for (path in logo(id)) {
                val request =
                    logoRequest(context, path, rasterSize = "w300")
                        .memoryCacheKey(logoKey(id))
                        .size((360 * density).roundToInt(), (76 * density).roundToInt())
                        .scale(Scale.FIT)
                        .build()
                val result = withTimeoutOrNull(1_600L) { imageLoader.execute(request) }
                if (result is SuccessResult) return@withTimeoutOrNull result.image
            }
            null
        }

    val scope = rememberCoroutineScope()
    val requests = remember { mutableMapOf<Long, Deferred<Image?>>() }

    fun keepRequest(id: Long?) {
        val iterator = requests.iterator()
        while (iterator.hasNext()) {
            val (titleId, request) = iterator.next()
            if (titleId != id) {
                request.cancel()
                iterator.remove()
            }
        }
    }

    suspend fun awaitLogo(id: Long): Image? {
        cached(id)?.let { return it }
        val request = requests.getOrPut(id) { scope.async(start = CoroutineStart.LAZY) { load(id) } }
        try {
            return request.await()
        } finally {
            if (request.isCompleted && requests[id] === request) requests.remove(id)
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val cachedLogo = cached(target.id)
    var presentation by remember { mutableStateOf(HeroPresentation(target, cachedLogo)) }
    LaunchedEffect(target.id, foreground) {
        keepRequest(target.id.takeIf { foreground })
        if (!foreground) return@LaunchedEffect
        val readyLogo = cached(target.id)
        if (readyLogo != null) {
            presentation = HeroPresentation(latestTarget, readyLogo)
        } else {
            val showText =
                launch {
                    delay(300)
                    presentation = HeroPresentation(latestTarget, null)
                }
            val loaded = awaitLogo(target.id)
            if (loaded != null) {
                showText.cancel()
                presentation = HeroPresentation(latestTarget, loaded)
            }
        }
    }
    val latestNeighbors by rememberUpdatedState(neighborTitleIds)
    LaunchedEffect(target.id, foreground) {
        if (foreground) {
            delay(180)
            val neighbors = latestNeighbors().distinct().filterNot { it == target.id }
            for (neighbor in neighbors) awaitLogo(neighbor)
        }
    }
    val display = if (cachedLogo != null) HeroPresentation(target, cachedLogo) else presentation
    val shown = if (display.card.id == target.id) target else display.card
    LaunchedEffect(shown.backdropPath) { onBackdropChanged?.invoke(shown.backdropPath) }
    Box(Modifier.fillMaxWidth().height(height)) {
        if (onBackdropChanged == null) TitleBackdrop(shown.backdropPath, Modifier.matchParentSize(), opacity = 0.82f)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.86f)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            HeroHeading(shown.name, display.logo, height = if (height < 175.dp) 42.dp else 64.dp)
            Text(
                shown.meta(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            shown.overview?.takeIf { height >= 185.dp }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = ((height.value.toInt() - 142) / 22).coerceIn(1, 3),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onPlay(shown) }) { Text("Redă") }
                OutlinedButton(onClick = { onDetails(shown) }) { Text("Detalii") }
                extraActions()
            }
        }
    }
}
