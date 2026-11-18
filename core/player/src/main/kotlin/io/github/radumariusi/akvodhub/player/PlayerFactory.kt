package io.github.radumariusi.akvodhub.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.radumariusi.akvodhub.parser.player.BufferPreset

/** Ce piste alege playerul singur: limbile audio și ale subtitrărilor, în ordinea preferinței (goală = oprite). */
data class TrackPreferences(
    val audioLanguages: List<String>,
    val subtitleLanguages: List<String>,
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
        passthrough: Boolean = false,
        nightMode: Boolean = false,
        buffer: BufferPreset = BufferPreset.NORMAL,
    ): ExoPlayer {
        val http =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
        val dataSource = DefaultDataSource.Factory(context, http)
        val renderers =
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioOutputPlaybackParams: Boolean,
                ): AudioSink {
                    val sink =
                        DefaultAudioSink
                            .Builder(context)
                            .setAudioProcessors(if (nightMode) arrayOf(NightAudioProcessor()) else emptyArray())
                            .build()
                    return object : ForwardingAudioSink(sink) {
                        override fun supportsFormat(format: Format): Boolean =
                            getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

                        override fun getFormatSupport(format: Format): Int =
                            if ((!passthrough || nightMode) && format.sampleMimeType != "audio/raw") {
                                AudioSink.SINK_FORMAT_UNSUPPORTED
                            } else {
                                super.getFormatSupport(format)
                            }
                    }
                }
            }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
        val player =
            ExoPlayer
                .Builder(context, renderers)
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
                .setLoadControl(
                    DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(buffer.minMs, buffer.maxMs, buffer.playMs, buffer.rebufferMs)
                        .setTargetBufferBytes(buffer.bytes)
                        .setPrioritizeTimeOverSizeThresholds(false)
                        .build(),
                ).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
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
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setPreferredAudioLanguages(*preferences.audioLanguages.toTypedArray())
        val subtitles = preferences.subtitleLanguages
        if (subtitles.isNotEmpty()) {
            builder
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setPreferredTextLanguages(*subtitles.toTypedArray())
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
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
