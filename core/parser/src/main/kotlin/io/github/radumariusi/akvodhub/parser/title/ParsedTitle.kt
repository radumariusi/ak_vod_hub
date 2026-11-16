package io.github.radumariusi.akvodhub.parser.title

/** Calitatea video, de la cea mai mică la cea mai mare (ordinea contează la sortare). */
enum class VideoQuality {
    SD,
    HD,
    FHD,
    UHD,
}

/** Formatul HDR, de la cel mai simplu la cel mai bun. */
enum class HdrFormat {
    HDR,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
}

enum class VideoCodec {
    H264,
    H265,
    AV1,
    VP9,
    XVID,
}

/**
 * Ce scoate [TitleParser] dintr-un nume de playlist, ex. `EN| The Matrix (1999) 4K HDR`.
 *
 * Limbile sunt coduri ISO 639-1 (`en`, `de`, `ro`…), în ordinea în care apar.
 * [MULTI] (`mul`) înseamnă „mai multe limbi” (ex. `MULTI`, `DUAL`, `MULTI-SUB`), fără să știm care.
 */
data class ParsedTitle(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val quality: VideoQuality? = null,
    val hdr: HdrFormat? = null,
    val codec: VideoCodec? = null,
    /** Limbile audio (dublaj / limba originală declarată). */
    val languages: List<String> = emptyList(),
    /** Limbile subtitrărilor declarate în nume (ex. `SUB RO`, `VOSTFR`, `MULTI-SUB`). */
    val subtitles: List<String> = emptyList(),
) {
    companion object {
        const val MULTI = "mul"
    }
}
