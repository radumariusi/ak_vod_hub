package io.github.radumariusi.akvodhub.parser.title

/**
 * Dicționarele parserului: coduri și nume de limbi, etichete de calitate, HDR, codec, sursă.
 * Toate cheile sunt cu majuscule, fără puncte (tokenizatorul le scoate).
 */
internal object TitleTags {
    /** Coduri de limbă / țară folosite de furnizorii IPTV → ISO 639-1. */
    val languageCodes: Map<String, String> =
        buildMap {
            fun add(
                iso: String,
                vararg codes: String,
            ) = codes.forEach { put(it, iso) }
            add("en", "EN", "ENG", "UK", "GB", "US", "USA")
            add("de", "DE", "GER", "DEU", "AT", "CH")
            add("fr", "FR", "FRE", "FRA")
            add("it", "IT", "ITA")
            add("es", "ES", "ESP", "SPA", "LAT", "LATINO", "MX")
            add("pt", "PT", "POR", "BR")
            add("nl", "NL", "DUT", "NLD", "BE")
            add("pl", "PL", "POL")
            add("ro", "RO", "ROM", "RON", "MD")
            add("ru", "RU", "RUS")
            add("tr", "TR", "TUR")
            add("ar", "AR", "ARA")
            add("el", "GR", "EL", "GRE")
            add("hu", "HU", "HUN")
            add("cs", "CZ", "CS", "CZE")
            add("sk", "SK", "SLO")
            add("sv", "SE", "SV", "SWE")
            add("no", "NO", "NOR")
            add("da", "DK", "DA", "DAN")
            add("fi", "FI", "FIN")
            add("sq", "AL", "SQ", "ALB")
            add("bg", "BG", "BUL")
            add("uk", "UA", "UKR")
            add("hi", "HI", "HIN", "IN")
            add("ja", "JP", "JA", "JPN")
            add("ko", "KR", "KO", "KOR")
            add("zh", "CN", "ZH", "CHI")
            add("fa", "IR", "FA", "PER")
            add("he", "IL", "HE", "HEB")
            add("sr", "RS", "SR", "SRB", "SRP")
            add("hr", "HR", "HRV", "CRO")
            add("bs", "BA", "BS", "BOS")
            add("mk", "MK", "MKD")
            add("sl", "SI", "SL", "SLV")
            add("lt", "LT", "LIT")
            add("lv", "LV", "LAV")
            add("et", "EE", "ET", "EST")
            add("ku", "KU", "KUR")
            add("sh", "EXYU", "YU")
            add("th", "TH", "THA")
            add("vi", "VN", "VI", "VIE")
            add("id", "ID", "IND")
            add("ur", "PK", "UR", "URD")
            add("ta", "TA", "TAM")
            add("te", "TE", "TEL")
        }

    /** Nume de limbi (în engleză și în limba respectivă) → ISO 639-1. */
    val languageNames: Map<String, String> =
        buildMap {
            fun add(
                iso: String,
                vararg names: String,
            ) = names.forEach { put(it, iso) }
            add("en", "ENGLISH", "ENGLEZA", "ENGLEZĂ")
            add("de", "GERMAN", "DEUTSCH", "GERMANA", "GERMANĂ")
            add("fr", "FRENCH", "FRANCAIS", "FRANÇAIS", "FRANCEZA", "FRANCEZĂ", "TRUEFRENCH", "VFF", "VFQ", "VF")
            add("it", "ITALIAN", "ITALIANO", "ITALIANA", "ITALIANĂ")
            add("es", "SPANISH", "ESPANOL", "ESPAÑOL", "CASTELLANO", "SPANIOLA", "SPANIOLĂ")
            add("pt", "PORTUGUESE", "PORTUGUES", "PORTUGUÊS", "BRASIL", "BRAZIL", "DUBLADO")
            add("nl", "DUTCH", "NEDERLANDS", "FLEMISH", "VLAAMS")
            add("pl", "POLISH", "POLSKI", "LEKTOR")
            add("ro", "ROMANIAN", "ROMANA", "ROMÂNA", "ROMÂNĂ", "ROMANESC", "ROMANESTI", "ROMÂNEȘTI", "DUBLAT")
            add("ru", "RUSSIAN", "RUSSKIY")
            add("tr", "TURKISH", "TURK", "TÜRK", "TURKCE", "TÜRKÇE")
            add("ar", "ARABIC")
            add("el", "GREEK")
            add("hu", "HUNGARIAN", "MAGYAR")
            add("cs", "CZECH")
            add("sk", "SLOVAK")
            add("sv", "SWEDISH", "SVENSKA")
            add("no", "NORWEGIAN", "NORSK")
            add("da", "DANISH", "DANSK")
            add("fi", "FINNISH", "SUOMI")
            add("sq", "ALBANIAN", "SHQIP")
            add("bg", "BULGARIAN")
            add("uk", "UKRAINIAN")
            add("hi", "HINDI")
            add("ja", "JAPANESE")
            add("ko", "KOREAN")
            add("zh", "CHINESE", "MANDARIN", "CANTONESE")
            add("fa", "PERSIAN", "FARSI")
            add("he", "HEBREW")
            add("sr", "SERBIAN")
            add("hr", "CROATIAN")
            add("bs", "BOSNIAN")
            add("ku", "KURDISH")
            add("th", "THAI")
            add("vi", "VIETNAMESE")
        }

    /** Etichete de subtitrare cu limba inclusă. */
    val subtitleLanguage: Map<String, String> =
        mapOf(
            "VOSTFR" to "fr",
            "STFR" to "fr",
            "VOSE" to "es",
            "LEGENDADO" to "pt",
            "LEG" to "pt",
            "SUBTITRAT" to "ro",
            "SUBTITRARE" to "ro",
            "NAPISY" to "pl",
            "UNTERTITEL" to "de",
            "ONDERTITELD" to "nl",
        )

    /** Cuvinte care arată că urmează / precedă limba subtitrării (`SUB RO`, `RO SUBS`). */
    val subtitleWords = setOf("SUB", "SUBS", "SUBBED", "SUBTITLE", "SUBTITLES", "SUBT", "STR", "VOST", "VO")

    val multiAudio = setOf("MULTI", "MULTIAUDIO", "MULTILANG", "MULTILANGUAGE", "DUAL", "DUALAUDIO", "MULTI-AUDIO")
    val multiSub = setOf("MULTISUB", "MULTISUBS")

    val quality: Map<String, VideoQuality> =
        buildMap {
            listOf("8K", "4K", "UHD", "2160P", "2160", "3840P", "4KUHD", "UHD4K").forEach { put(it, VideoQuality.UHD) }
            listOf("FHD", "1080P", "1080I", "1080", "FULLHD").forEach { put(it, VideoQuality.FHD) }
            listOf("HD", "720P", "720", "HDTV").forEach { put(it, VideoQuality.HD) }
            listOf("SD", "480P", "576P", "480", "576", "DVDRIP", "DVD", "LQ").forEach { put(it, VideoQuality.SD) }
        }

    val hdr: Map<String, HdrFormat> =
        mapOf(
            "HDR" to HdrFormat.HDR,
            "HDR10" to HdrFormat.HDR10,
            "HDR10+" to HdrFormat.HDR10_PLUS,
            "HDR10PLUS" to HdrFormat.HDR10_PLUS,
            "DV" to HdrFormat.DOLBY_VISION,
            "DOVI" to HdrFormat.DOLBY_VISION,
            "DOLBYVISION" to HdrFormat.DOLBY_VISION,
        )

    val codec: Map<String, VideoCodec> =
        mapOf(
            "HEVC" to VideoCodec.H265,
            "H265" to VideoCodec.H265,
            "X265" to VideoCodec.H265,
            "H264" to VideoCodec.H264,
            "X264" to VideoCodec.H264,
            "AVC" to VideoCodec.H264,
            "AV1" to VideoCodec.AV1,
            "VP9" to VideoCodec.VP9,
            "XVID" to VideoCodec.XVID,
            "DIVX" to VideoCodec.XVID,
        )

    /** Etichete fără informație utilă pentru noi (sursă, audio, ediție), care doar se scot din titlu. */
    val noise =
        setOf(
            "BLURAY",
            "BLU",
            "RAY",
            "BDRIP",
            "BRRIP",
            "BDREMUX",
            "REMUX",
            "WEBDL",
            "WEB",
            "DL",
            "WEBRIP",
            "HDRIP",
            "DVDSCR",
            "HDCAM",
            "CAM",
            "TS",
            "TC",
            "TELESYNC",
            "HDTS",
            "PROPER",
            "REPACK",
            "INTERNAL",
            "LIMITED",
            "EXTENDED",
            "UNRATED",
            "UNCUT",
            "REMASTERED",
            "DIRECTORS",
            "DIRECTOR'S",
            "CUT",
            "THEATRICAL",
            "IMAX",
            "AAC",
            "AC3",
            "EAC3",
            "DDP",
            "DD",
            "DD5",
            "DD+",
            "DTS",
            "DTSHD",
            "ATMOS",
            "TRUEHD",
            "FLAC",
            "MP3",
            "5",
            "1",
            "7",
            "2",
            "10BIT",
            "8BIT",
            "60FPS",
            "HSBS",
            "SBS",
            "RAW",
            "HQ",
            "VIP",
            "AMZN",
            "NF",
            "DSNP",
            "HMAX",
            "ATVP",
            "PMTP",
            "HULU",
            "DOLBY",
            "VISION",
            "NEW",
            "DUBBED",
            "DUB",
        )

    /** Etichete care apar doar în prefixul de furnizor (ex. `|NF| Titlu`, `VOD: Titlu`). */
    val platforms =
        setOf(
            "VOD",
            "NETFLIX",
            "AMAZON",
            "PRIME",
            "DISNEY",
            "D",
            "HBO",
            "MAX",
            "APPLE",
            "APPLETV",
            "PARAMOUNT",
            "SKY",
            "TOP",
            "A+",
            "AMZ",
            "D+",
            "PRMT",
            // Marcaj de furnizor întâlnit lângă 4K/HDR, fără o limbă audio stabilită.
            "SC",
            "MOVIES",
            "MOVIE",
            "FILME",
            "FILM",
            "SERIES",
            "SERIALE",
            "KIDS",
        )

    /** Etichete clare care, oriunde apar după primul cuvânt, marchează sfârșitul titlului. */
    val strong =
        setOf(
            "8K",
            "4K",
            "UHD",
            "2160P",
            "3840P",
            "1080P",
            "1080I",
            "720P",
            "480P",
            "576P",
            "FHD",
            "FULLHD",
            "4KUHD",
            "UHD4K",
            "HDR",
            "HDR10",
            "HDR10+",
            "HDR10PLUS",
            "DOVI",
            "HEVC",
            "H265",
            "X265",
            "H264",
            "X264",
            "XVID",
            "DIVX",
            "BLURAY",
            "BDRIP",
            "BRRIP",
            "BDREMUX",
            "REMUX",
            "WEBDL",
            "WEBRIP",
            "HDRIP",
            "DVDRIP",
            "HDTV",
            "HDCAM",
            "MULTI",
            "MULTISUB",
            "MULTISUBS",
            "MULTIAUDIO",
            "VOSTFR",
            "VOSE",
            "SUBBED",
            "10BIT",
            "ATMOS",
            "TRUEHD",
            "TRUEFRENCH",
        )
}
