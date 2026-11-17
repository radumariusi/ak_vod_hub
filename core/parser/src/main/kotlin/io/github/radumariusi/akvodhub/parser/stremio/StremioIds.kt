package io.github.radumariusi.akvodhub.parser.stremio

import java.net.URLEncoder

/** Adrese și ID-uri din protocolul addon-urilor Stremio. */
object StremioIds {
    /** Addon-ul implicit pentru subtitrări. */
    const val OPENSUBTITLES_MANIFEST = "https://opensubtitles-v3.strem.io/manifest.json"

    private val imdbPattern = Regex("""^tt\d{5,10}$""")

    fun isImdb(id: String?): Boolean = id != null && imdbPattern.matches(id)

    /**
     * Adresa manifestului din ce a scris utilizatorul: acceptă `stremio://…`, adresa fără `https://`,
     * cu sau fără `/manifest.json` la final. null dacă nu arată a adresă.
     */
    fun normalizeManifestUrl(input: String): String? {
        var url = input.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        url =
            when {
                url.startsWith("stremio://", ignoreCase = true) -> "https://" + url.substring("stremio://".length)
                url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
                else -> "https://$url"
            }
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?')
        if (host.isEmpty() || (!host.contains('.') && !host.startsWith("localhost"))) return null
        url = url.substringBefore('?').substringBefore('#').trimEnd('/')
        return if (url.endsWith("/manifest.json")) url else "$url/manifest.json"
    }

    /** Adresa de bază a addon-ului (manifestul fără `/manifest.json`). */
    fun baseUrl(manifestUrl: String): String = manifestUrl.removeSuffix("/manifest.json").trimEnd('/')

    /** Tipul Stremio: `movie` sau `series`. */
    fun type(series: Boolean): String = if (series) "series" else "movie"

    /** ID-ul video: `tt0133093` la filme, `tt0903747:1:2` la episoade. null dacă lipsesc sezonul / episodul. */
    fun videoId(
        imdbId: String,
        series: Boolean,
        season: Int?,
        episode: Int?,
    ): String? =
        when {
            !series -> imdbId
            season == null || episode == null -> null
            else -> "$imdbId:$season:$episode"
        }

    /** `{bază}/subtitles/{tip}/{id}.json`; „:” din ID rămâne necodat, ca în clienții Stremio. */
    fun subtitlesUrl(
        baseUrl: String,
        type: String,
        videoId: String,
    ): String {
        val id = URLEncoder.encode(videoId, "UTF-8").replace("%3A", ":").replace("+", "%20")
        return "$baseUrl/subtitles/$type/$id.json"
    }
}
