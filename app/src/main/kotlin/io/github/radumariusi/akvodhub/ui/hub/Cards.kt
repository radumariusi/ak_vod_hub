@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.allowRgb565
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadge
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.catalog.PosterSize
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import io.github.radumariusi.akvodhub.ui.touch.Card

val LocalPosterBadges = staticCompositionLocalOf { PosterBadges.defaults }

val LocalPosterSize = staticCompositionLocalOf { PosterSize.MEDIUM }

val PosterWidth: Dp
    @Composable get() = LocalPosterSize.current.widthDp.dp
val PosterHeight: Dp
    @Composable get() = LocalPosterSize.current.heightDp.dp

/** URL-uri de imagini TMDB. Titlurile locale au deja un URL complet (logo-ul din playlist). */
object Images {
    fun poster(path: String?, size: PosterSize = PosterSize.MEDIUM) = url(path, size.imageSize)

    fun logo(path: String, rasterSize: String) =
        url(
            path,
            if (path.endsWith(".svg", ignoreCase = true)) "original" else rasterSize,
        )

    fun hero(path: String?) = url(path, "w780")

    fun thumbnail(path: String?) = url(path, "w300")

    fun backdrop(path: String?) = url(path, "w1280")

    fun profile(path: String?) = url(path, "w185")

    private fun url(
        path: String?,
        size: String,
    ): String? =
        when {
            path.isNullOrBlank() -> null
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> "${TmdbClient.IMAGE_BASE}$size$path"
        }
}

@Composable
private fun posterModel(url: String): Any {
    if (!url.startsWith(TmdbClient.IMAGE_BASE) || !url.endsWith(".jpg", ignoreCase = true)) return url
    val context = LocalContext.current
    return remember(context, url) {
        ImageRequest
            .Builder(context)
            .data(url)
            .allowHardware(false)
            .allowRgb565(true)
            .build()
    }
}

fun qualityLabel(ordinal: Int?): String? =
    ordinal?.let { VideoQuality.entries.getOrNull(it) }?.let { SourceGrouping.qualityLabel(it) }

/** Ex. „2021 · ★ 7,8 · 4K”. */
fun TitleCard.meta(): String =
    listOfNotNull(
        year?.toString(),
        rating?.takeIf { it > 0 }?.let { "TMDB %.1f".format(it) } ?: "TMDB —",
        qualityLabel(bestQuality),
        imdbRating?.let { "IMDb %.1f".format(it) } ?: "IMDb —",
        dubLabel,
    ).joinToString(" · ")

/**
 * Posterul unui titlu, cu numele dedesubt. [requestFocus] = true cere focusul (la întoarcerea din detalii,
 * focusul revine pe cardul deschis), apoi anunță prin [onFocusRequested].
 */
@Composable
fun PosterCard(
    card: TitleCard,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusRequested: () -> Unit = {},
    supportingText: String? = null,
    landscape: Boolean = false,
    showTitle: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val width = if (landscape) PosterWidth * 2.05f else PosterWidth
    val height = if (landscape) width * (9f / 16) else PosterHeight
    val badges = LocalPosterBadges.current
    val ratingsAtBottom = !landscape && !showTitle
    val requester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onFocusRequested()
        }
    }
    Box(
        modifier
            .width(width + 16.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier =
                Modifier
                    .width(width)
                    .focusRequester(requester)
                    .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Box(Modifier.size(width, height).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val poster =
                    (if (landscape) Images.thumbnail(card.backdropPath) else null)
                        ?: Images.poster(card.posterPath, LocalPosterSize.current)
                if (poster == null) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                } else {
                    AsyncImage(
                        model = posterModel(poster),
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (ratingsAtBottom) {
                        if (PosterBadge.DUB in badges) card.dubLabel?.let { PosterBadgeText(it) }
                    } else {
                        if (PosterBadge.TMDB in badges) PosterBadgeText(card.tmdbBadge ?: "★ —")
                        if (PosterBadge.IMDB in badges) PosterBadgeText(card.imdbBadge ?: "IMDb —")
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (PosterBadge.QUALITY in badges) {
                        if (card.bestQuality == VideoQuality.UHD.ordinal) PosterBadgeText("4K")
                        card.hdrBadge?.let { PosterBadgeText(it) }
                    }
                    if (PosterBadge.NEW in badges && card.isNew) PosterBadgeText("Nou")
                }
                if (landscape && supportingText != null) {
                    PosterBadgeText(supportingText, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
                if (ratingsAtBottom && (PosterBadge.TMDB in badges || PosterBadge.IMDB in badges)) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xD9111319))
                            .padding(start = 2.dp, end = 2.dp, bottom = 5.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (PosterBadge.TMDB in badges) PosterBadgeText(card.tmdbBadge ?: "★ —")
                        if (PosterBadge.IMDB in badges) PosterBadgeText(card.imdbBadge ?: "IMDb —")
                    }
                }
                if (showTitle && PosterBadge.DUB in badges) {
                    card.dubLabel?.let { label ->
                        PosterBadgeText(
                            label,
                            Modifier.align(Alignment.BottomStart).padding(4.dp).padding(bottom = 4.dp),
                        )
                    }
                }
                if (PosterBadge.PROGRESS in badges && card.watchProgress > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0xAA000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    card.watchProgress.toFloat(),
                                ).height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            if (showTitle) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterBadgeText(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(Color(0xCC000000), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
