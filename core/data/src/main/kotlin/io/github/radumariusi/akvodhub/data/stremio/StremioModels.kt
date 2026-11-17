package io.github.radumariusi.akvodhub.data.stremio

import io.github.radumariusi.akvodhub.parser.stremio.AddonSubtitle
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
        )
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

    private fun JsonObject.strings(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
