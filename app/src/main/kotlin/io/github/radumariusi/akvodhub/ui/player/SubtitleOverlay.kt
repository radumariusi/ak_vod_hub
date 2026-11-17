@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.player.SubtitleStyle
import io.github.radumariusi.akvodhub.parser.subtitle.SubtitleDocument
import io.github.radumariusi.akvodhub.player.PlayerUiState
import io.github.radumariusi.akvodhub.player.PlayerViewModel
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import androidx.annotation.OptIn as AndroidOptIn

/**
 * Subtitrarea din addon, desenată de aplicație peste video (nu prin player): așa se poate muta în timp
 * (sincronizare) instant, fără să repornim sursa IPTV.
 */
@Composable
fun ExternalSubtitles(
    document: SubtitleDocument,
    player: Player,
    offsetMs: Long,
    scale: Int,
    style: SubtitleStyle,
    raised: Boolean,
    modifier: Modifier,
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(document, player, offsetMs) {
        while (true) {
            text = document.textAt(player.currentPosition - offsetMs)
            delay(TICK_MS)
        }
    }
    if (text.isEmpty()) return
    val size = (BASE_SIZE_SP * scale / 100f).sp
    val base =
        TextStyle(
            fontSize = size,
            lineHeight = size * 1.2f,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    Box(
        modifier
            .padding(start = 80.dp, end = 80.dp, bottom = if (raised) 250.dp else 48.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (style == SubtitleStyle.WHITE_BOX) {
            Text(
                text,
                style = base.copy(color = Color.White),
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        } else {
            val stroke = with(LocalDensity.current) { size.toPx() * 0.14f }
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text,
                    style = base.copy(color = Color.Black, drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)),
                )
                Text(text, style = base.copy(color = textColor(style)))
            }
        }
    }
}

/** Panoul „Ajustează”: sincronizarea subtitrării din addon, mărimea și stilul (se salvează în Setări). */
@Composable
fun SubtitleAdjustPanel(
    state: PlayerUiState,
    scale: Int,
    style: SubtitleStyle,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    ThemedPanel(modifier.padding(20.dp).widthIn(max = 740.dp).verticalScroll(rememberScrollState())) {
        Text("Ajustează subtitrarea", style = MaterialTheme.typography.titleLarge)
        state.externalLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondary) }
        if (state.externalSubtitle != null) {
            Text(
                "Sincronizare: ${formatOffset(state.subtitleOffsetMs)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.adjustSubtitleOffset(-1_000) },
                    modifier = Modifier.focusRequester(focus),
                ) { Text("−1 s") }
                OutlinedButton(onClick = { viewModel.adjustSubtitleOffset(-100) }) { Text("−0,1 s") }
                OutlinedButton(onClick = { viewModel.adjustSubtitleOffset(100) }) { Text("+0,1 s") }
                OutlinedButton(onClick = { viewModel.adjustSubtitleOffset(1_000) }) { Text("+1 s") }
                OutlinedButton(onClick = viewModel::resetSubtitleOffset) { Text("0") }
            }
            Text(
                "Textul apare înaintea replicii? Apasă +. Apare după? Apasă −.",
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
        } else {
            Text(
                "Sincronizarea se poate face la subtitrările din addon-uri (cele din sursă vin deja potrivite).",
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
        }
        Text("Mărime: $scale%", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val smaller = PlaybackSettings.SCALES.lastOrNull { it < scale }
            val bigger = PlaybackSettings.SCALES.firstOrNull { it > scale }
            OutlinedButton(
                onClick = { smaller?.let(viewModel::setSubtitleScale) },
                enabled = smaller != null,
                modifier = if (state.externalSubtitle == null) Modifier.focusRequester(focus) else Modifier,
            ) { Text("A−") }
            OutlinedButton(onClick = { bigger?.let(viewModel::setSubtitleScale) }, enabled = bigger != null) {
                Text("A+")
            }
        }
        Text("Stil", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SubtitleStyle.entries.forEach { s ->
                if (s == style) {
                    Button(onClick = {}) { Text(s.label) }
                } else {
                    OutlinedButton(onClick = { viewModel.setSubtitleStyle(s) }) { Text(s.label) }
                }
            }
        }
        Button(onClick = onClose) { Text("Gata") }
    }
}

/** Aceeași mărime și același stil pentru subtitrările din sursă (desenate de Media3). */
@AndroidOptIn(UnstableApi::class)
object SubtitleLook {
    fun applyTo(
        view: PlayerView,
        scale: Int,
        style: SubtitleStyle,
    ) {
        val subtitles = view.subtitleView ?: return
        subtitles.setApplyEmbeddedStyles(false)
        subtitles.setApplyEmbeddedFontSizes(false)
        subtitles.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale / 100f)
        subtitles.setStyle(
            when (style) {
                SubtitleStyle.WHITE_OUTLINE -> {
                    outline(android.graphics.Color.WHITE)
                }

                SubtitleStyle.YELLOW_OUTLINE -> {
                    outline(YELLOW)
                }

                SubtitleStyle.WHITE_BOX -> {
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        BOX,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        android.graphics.Color.BLACK,
                        null,
                    )
                }
            },
        )
    }

    private fun outline(color: Int) =
        CaptionStyleCompat(
            color,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null,
        )

    private val YELLOW = 0xFFFFEB3B.toInt()
    private val BOX = 0xC0000000.toInt()
}

private fun textColor(style: SubtitleStyle): Color =
    if (style == SubtitleStyle.YELLOW_OUTLINE) Color(0xFFFFEB3B) else Color.White

/** Ex. „+1,3 s (mai târziu)”, „−0,5 s (mai devreme)”, „0 s”. */
fun formatOffset(ms: Long): String {
    if (ms == 0L) return "0 s"
    val value = String.format(Locale.forLanguageTag("ro"), "%.1f", abs(ms) / 1000.0)
    return if (ms > 0) "+$value s (mai târziu)" else "−$value s (mai devreme)"
}

private const val TICK_MS = 100L
private const val BASE_SIZE_SP = 30f
