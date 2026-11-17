@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
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

    fun logo(path: String?) = url(path, "w500")

    fun hero(path: String?) = url(path, "w780")

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

fun qualityLabel(ordinal: Int?): String? =
    ordinal?.let { VideoQuality.entries.getOrNull(it) }?.let { SourceGrouping.qualityLabel(it) }

/** Ex. „2021 · ★ 7,8 · 4K”. */
fun TitleCard.meta(): String =
    listOfNotNull(
        year?.toString(),
        rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
        qualityLabel(bestQuality),
        imdbRating?.let { "IMDb %.1f".format(it) },
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
) {
    val badges = LocalPosterBadges.current
    val requester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onFocusRequested()
        }
    }
    Column(modifier.width(PosterWidth)) {
        Card(
            onClick = onClick,
            modifier =
                Modifier
                    .size(PosterWidth, PosterHeight)
                    .focusRequester(requester)
                    .onFocusChanged { if (it.isFocused) onFocused() },
            scale = CardDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                val poster = Images.poster(card.posterPath, LocalPosterSize.current)
                if (poster == null) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                } else {
                    AsyncImage(
                        model = poster,
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (PosterBadge.TMDB in badges) card.tmdbBadge?.let { PosterBadgeText(it) }
                    if (PosterBadge.IMDB in badges) card.imdbBadge?.let { PosterBadgeText(it) }
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
                if (PosterBadge.DUB in badges) {
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
        }
        Text(
            card.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).height(18.dp),
        )
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
