package io.github.radumariusi.akvodhub.data.stremio

import io.github.radumariusi.akvodhub.parser.stremio.AddonSubtitle
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import io.github.radumariusi.akvodhub.parser.stremio.SubtitleLanguages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Ce ne interesează din `manifest.json`-ul unui addon. */
data class StremioManifest(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    /** Resursele (ex. `subtitles`, `catalog`), fiecare cu tipurile și prefixele de ID acceptate (null = toate). */
    val resources: List<StremioResource>,
    /** Cataloagele (liste de titluri: top-uri, liste MDBList / Trakt etc.). */
    val catalogs: List<StremioCatalog> = emptyList(),
) {
    fun resource(name: String): StremioResource? = resources.firstOrNull { it.name == name }

    /** Addon-ul dă subtitrări pentru tipul ([type] = `movie` / `series`) și ID-ul ăsta (ex. `tt…`). */
    fun supportsSubtitles(
        type: String,
        videoId: String,
    ): Boolean {
        val res = resource("subtitles") ?: return false
        if (res.types != null && type !in res.types) return false
        if (res.idPrefixes != null && res.idPrefixes.none { videoId.startsWith(it) }) return false
        return true
    }
}

/**
 * Un catalog din manifest. [required] = parametrii obligatorii (ex. `search`): cataloagele cu așa ceva nu se pot
 * afișa ca rând. [skip] = acceptă paginare (`skip=100`).
 */
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String,
    val required: List<String>,
    val skip: Boolean,
) {
    /** Se poate arăta pe Acasă: filme sau seriale, fără parametri obligatorii. */
    val browsable: Boolean get() = type in BROWSABLE_TYPES && required.isEmpty()

    private companion object {
        val BROWSABLE_TYPES = setOf("movie", "series")
    }
}

/** Un titlu dintr-un catalog: ID-ul Stremio (`tt…`, `tmdb:…`), tipul, numele și anul. */
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val year: Int?,
    val imdbId: String?,
    val tmdbId: Long?,
)

data class StremioResource(
    val name: String,
    val types: List<String>?,
    val idPrefixes: List<String>?,
)

/** Citirea răspunsurilor addon-urilor; tolerantă (câmpuri lipsă, tipuri greșite). */
object StremioJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** null dacă nu e un manifest Stremio (lipsește `id` sau `resources`). */
    fun manifest(body: String): StremioManifest? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val id = root.str("id") ?: return null
        val resourcesJson = root["resources"] as? JsonArray ?: return null
        val types = root.strings("types")
        val prefixes = root.strings("idPrefixes")
        val resources =
            resourcesJson.mapNotNull { r ->
                when (r) {
                    is JsonPrimitive -> {
                        r.contentOrNull?.let { StremioResource(it, types, prefixes) }
                    }

                    is JsonObject -> {
                        r.str("name")?.let { name ->
                            StremioResource(name, r.strings("types") ?: types, r.strings("idPrefixes") ?: prefixes)
                        }
                    }

                    else -> {
                        null
                    }
                }
            }
        return StremioManifest(
            id = id,
            name = root.str("name")?.takeIf { it.isNotBlank() } ?: id,
            version = root.str("version"),
            description = root.str("description"),
            resources = resources,
            catalogs = catalogs(root["catalogs"] as? JsonArray),
        )
    }

    private fun catalogs(list: JsonArray?): List<StremioCatalog> =
        list.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val type = obj.str("type") ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            // Formatul nou: "extra": [{"name":"genre","isRequired":false}]; cel vechi: "extraRequired" / "extraSupported".
            val extra = (obj["extra"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val required =
                extra.filter { it.bool("isRequired") }.mapNotNull { it.str("name") } +
                    obj.strings("extraRequired").orEmpty()
            val supported = extra.mapNotNull { it.str("name") } + obj.strings("extraSupported").orEmpty()
            StremioCatalog(
                type = type,
                id = id,
                name = obj.str("name")?.takeIf { it.isNotBlank() } ?: id,
                required = required.distinct(),
                skip = "skip" in supported,
            )
        }

    /** Titlurile din `{"metas":[{"id","type","name","releaseInfo"…}]}`; intrările fără ID sau nume se sar. */
    fun metas(body: String): List<StremioMeta> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val list = root["metas"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val name = obj.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val imdb = StremioIds.imdbId(id) ?: StremioIds.imdbId(obj.str("imdb_id"))
            val tmdb =
                StremioIds.tmdbId(id)
                    ?: obj.str("moviedb_id")?.toLongOrNull()
                    ?: obj.str("tmdb_id")?.toLongOrNull()
            StremioMeta(
                id = id,
                type = obj.str("type").orEmpty(),
                name = name,
                year = StremioIds.year(obj.str("releaseInfo")) ?: StremioIds.year(obj.str("year")),
                imdbId = imdb,
                tmdbId = tmdb?.takeIf { it > 0 },
            )
        }
    }

    /** Lista din `{"subtitles":[{"id","url","lang"}]}`; intrările fără URL se sar. */
    fun subtitles(
        body: String,
        addon: String,
    ): List<AddonSubtitle> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val list = root["subtitles"] as? JsonArray ?: return emptyList()
        return list.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val url = obj.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (url == null) return@mapIndexedNotNull null
            val lang = obj.str("lang").orEmpty()
            AddonSubtitle(
                id = obj.str("id") ?: "$addon-$index",
                url = url,
                rawLanguage = lang,
                language = SubtitleLanguages.toIso1(lang),
                addon = addon,
            )
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

    private fun JsonObject.strings(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
