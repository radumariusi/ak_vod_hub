package io.github.radumariusi.akvodhub.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Ce piste alege playerul singur: limbile audio în ordinea preferinței și limba subtitrării (null = oprite). */
data class TrackPreferences(
    val audioLanguages: List<String>,
    val subtitleLanguage: String?,
)

/**
 * Creează playerul Media3: decodoarele hardware ale TV-ului primele, iar pentru audio pe care TV-ul nu îl
 * decodează (AC3, EAC3, DTS, TrueHD pe multe box-uri) decodorul software FFmpeg. Linkurile IPTV fac des
 * redirecționări http → https, deci le permitem.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {
    private const val USER_AGENT = "AKVodHub (Linux; Android) Media3"

    fun create(
        context: Context,
        preferences: TrackPreferences,
    ): ExoPlayer {
        val http =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
        val dataSource = DefaultDataSource.Factory(context, http)
        val renderers =
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
        val player =
            ExoPlayer
                .Builder(context, renderers)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(SEEK_MS)
                .setSeekForwardIncrementMs(SEEK_MS)
                .build()
        player.trackSelectionParameters = apply(player.trackSelectionParameters, preferences)
        return player
    }

    fun apply(
        params: TrackSelectionParameters,
        preferences: TrackPreferences,
    ): TrackSelectionParameters {
        val builder =
            params
                .buildUpon()
                .setPreferredAudioLanguages(*preferences.audioLanguages.toTypedArray())
        val subtitle = preferences.subtitleLanguage
        if (subtitle != null) {
            builder.setPreferredTextLanguages(subtitle).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        return builder.build()
    }

    /** Pentru Setări: decodorul FFmpeg e încărcat (biblioteca nativă există pentru procesorul TV-ului). */
    fun ffmpegStatus(): String =
        try {
            if (FfmpegLibrary.isAvailable()) {
                "disponibil (FFmpeg ${FfmpegLibrary.getVersion() ?: "?"})"
            } else {
                "indisponibil"
            }
        } catch (e: Throwable) {
            "indisponibil"
        }

    const val SEEK_MS = 10_000L
}
