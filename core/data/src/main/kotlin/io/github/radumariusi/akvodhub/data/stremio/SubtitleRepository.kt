package io.github.radumariusi.akvodhub.data.stremio

import io.github.radumariusi.akvodhub.parser.stremio.AddonSubtitle
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import io.github.radumariusi.akvodhub.parser.subtitle.SubtitleCharset
import io.github.radumariusi.akvodhub.parser.subtitle.SubtitleDocument
import io.github.radumariusi.akvodhub.parser.subtitle.SubtitleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

/** Rezultatul căutării în addon-uri: subtitrările găsite și, pe scurt, ce n-a mers. */
data class AddonSubtitles(
    val subtitles: List<AddonSubtitle>,
    /** Ex. „OpenSubtitles v3: eroarea 503”; gol dacă toate addon-urile au răspuns. */
    val problems: List<String>,
)

/**
 * Subtitrările din addon-urile Stremio: caută în toate addon-urile cu resursa `subtitles` (în paralel),
 * apoi descarcă și citește subtitrarea aleasă. Listele și fișierele se țin în memorie cât rulează aplicația.
 */
class SubtitleRepository(
    private val client: StremioClient,
    private val addons: StremioAddons,
) {
    private val listCache = Collections.synchronizedMap(LinkedHashMap<String, List<AddonSubtitle>>())
    private val documents =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, SubtitleDocument>(8, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SubtitleDocument>?) =
                    size > MAX_DOCUMENTS
            },
        )

    /** Manifestele addon-urilor (o dată pe sesiune), ca să știm ce tipuri și ID-uri acceptă. */
    private val manifests = Collections.synchronizedMap(HashMap<String, StremioManifest>())

    suspend fun find(
        imdbId: String,
        series: Boolean,
        season: Int?,
        episode: Int?,
    ): AddonSubtitles {
        val none = AddonSubtitles(emptyList(), emptyList())
        val videoId = StremioIds.videoId(imdbId, series, season, episode) ?: return none
        val type = StremioIds.type(series)
        val installed = addons.addons.value.filter { it.resources.isEmpty() || "subtitles" in it.resources }
        if (installed.isEmpty()) return none
        val results =
            coroutineScope {
                installed
                    .map { addon ->
                        async { addon to runCatchingAddon { fromAddon(addon, type, videoId) } }
                    }.awaitAll()
            }
        val problems =
            results.mapNotNull { (addon, r) -> r.exceptionOrNull()?.let { "${addon.name}: ${reason(it)}" } }
        return AddonSubtitles(results.flatMap { it.second.getOrNull().orEmpty() }, problems)
    }

    private suspend fun fromAddon(
        addon: StremioAddon,
        type: String,
        videoId: String,
    ): List<AddonSubtitle> {
        val key = "${addon.manifestUrl}|$type|$videoId"
        listCache[key]?.let { return it }
        val manifest =
            manifests[addon.manifestUrl]
                ?: runCatching { client.manifest(addon.manifestUrl) }
                    .getOrNull()
                    ?.also { manifests[addon.manifestUrl] = it }
        if (manifest != null && !manifest.supportsSubtitles(type, videoId)) return emptyList()
        val list =
            withTimeoutOrNull(LIST_TIMEOUT_MS) { client.subtitles(addon, type, videoId) }
                ?: throw StremioException("nu răspunde")
        listCache[key] = list
        return list
    }

    /** Descarcă și citește subtitrarea; aruncă [StremioException] dacă nu se poate. */
    suspend fun load(subtitle: AddonSubtitle): SubtitleDocument {
        documents[subtitle.url]?.let { return it }
        val bytes =
            withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { client.download(subtitle.url) }
                ?: throw StremioException("descărcarea durează prea mult")
        if (isZip(bytes)) throw StremioException("fișier arhivat (zip), nesuportat")
        val document = SubtitleParser.parse(SubtitleCharset.decode(bytes, subtitle.language))
        if (document.isEmpty) throw StremioException("fișierul nu e o subtitrare citibilă")
        documents[subtitle.url] = document
        return document
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private suspend fun <T> runCatchingAddon(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        private const val MAX_DOCUMENTS = 6
        private const val LIST_TIMEOUT_MS = 20_000L
        private const val DOWNLOAD_TIMEOUT_MS = 30_000L

        fun reason(e: Throwable): String =
            when (e) {
                is StremioException -> e.message ?: "eroare"
                is java.net.UnknownHostException -> "fără conexiune la server"
                is java.net.SocketTimeoutException -> "nu răspunde"
                is java.io.IOException -> "eroare de rețea"
                else -> "răspuns necunoscut"
            }
    }
}
