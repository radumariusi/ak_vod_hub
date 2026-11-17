package io.github.radumariusi.akvodhub.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** O pistă audio sau de subtitrare, gata de afișat în meniul playerului. */
data class TrackOption(
    val type: Int,
    val group: TrackGroup,
    val index: Int,
    val label: String,
    val selected: Boolean,
    val supported: Boolean,
)

/** Pistele audio / subtitrări ale sursei curente și alegerea lor. */
@OptIn(UnstableApi::class)
object TrackOptions {
    private val romanian = Locale.forLanguageTag("ro")

    fun list(
        tracks: Tracks,
        type: Int,
    ): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        for (group in tracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val supported = group.isTrackSupported(i)
                result +=
                    TrackOption(
                        type = type,
                        group = group.mediaTrackGroup,
                        index = i,
                        label = label(format, type, result.size + 1) + if (supported) "" else " (nesuportată)",
                        selected = group.isTrackSelected(i),
                        supported = supported,
                    )
            }
        }
        return result
    }

    /** Limbile pistelor de tipul dat, așa cum le dă sursa (null = nedeclarată). */
    fun languages(
        tracks: Tracks,
        type: Int,
    ): List<String?> =
        tracks.groups
            .filter { it.type == type }
            .flatMap { g ->
                (0 until g.length)
                    .filter { g.isTrackSupported(it) }
                    .map { i -> g.getTrackFormat(i).language?.takeIf { it != C.LANGUAGE_UNDETERMINED } }
            }

    fun select(
        player: Player,
        option: TrackOption,
    ) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(option.type, false)
                .setOverrideForType(TrackSelectionOverride(option.group, option.index))
                .build()
    }

    fun disableSubtitles(player: Player) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
    }

    /** Codecul video al sursei (ex. „HEVC”), pentru mesajul „video nesuportat”. */
    fun videoCodec(tracks: Tracks): String? =
        tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            ?.getTrackFormat(0)
            ?.let { f -> codecName(f.sampleMimeType) + (f.height.takeIf { it > 0 }?.let { " ${it}p" } ?: "") }

    /** Ex. „Engleză · EAC3 5.1” sau „Română · SRT”. */
    fun label(
        format: Format,
        type: Int,
        number: Int,
    ): String {
        val language =
            format.language
                ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
                ?.let { languageName(it) }
        val channels =
            if (type == C.TRACK_TYPE_AUDIO) {
                when (format.channelCount) {
                    1 -> "mono"
                    2 -> "stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    Format.NO_VALUE -> null
                    else -> "${format.channelCount} canale"
                }
            } else {
                null
            }
        val forced = if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) "forțată" else null
        return listOfNotNull(
            language ?: format.label ?: "Pista $number",
            format.label?.takeIf { language != null && it.isNotBlank() },
            codecName(format.sampleMimeType),
            channels,
            forced,
        ).joinToString(" · ")
    }

    private fun languageName(code: String): String {
        val name = Locale.forLanguageTag(code).getDisplayLanguage(romanian)
        return if (name.isNullOrBlank() || name == code) {
            code.uppercase()
        } else {
            name.replaceFirstChar { it.titlecase(romanian) }
        }
    }

    private fun codecName(mime: String?): String? =
        when (mime) {
            null -> null
            "audio/ac3" -> "AC3"
            "audio/eac3", "audio/eac3-joc" -> "EAC3"
            "audio/ac4" -> "AC4"
            "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS"
            "audio/true-hd" -> "TrueHD"
            "audio/mp4a-latm" -> "AAC"
            "audio/mpeg", "audio/mpeg-L2", "audio/mpeg-L1" -> "MP3"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/flac" -> "FLAC"
            "application/x-subrip" -> "SRT"
            "text/x-ssa" -> "ASS"
            "text/vtt" -> "VTT"
            "application/pgs" -> "PGS"
            "application/vobsub" -> "VobSub"
            "application/dvbsubs" -> "DVB"
            "video/hevc" -> "HEVC"
            "video/avc" -> "H264"
            "video/av01" -> "AV1"
            "video/x-vnd.on2.vp9" -> "VP9"
            "video/dolby-vision" -> "Dolby Vision"
            "video/mp4v-es" -> "MPEG-4"
            "video/mpeg2" -> "MPEG-2"
            else -> mime.substringAfter('/').uppercase()
        }
}
