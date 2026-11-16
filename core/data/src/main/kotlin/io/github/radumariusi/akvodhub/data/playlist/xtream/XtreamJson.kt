package io.github.radumariusi.akvodhub.data.playlist.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

/**
 * Citirea răspunsurilor Xtream (`player_api.php`).
 *
 * Panourile Xtream sunt inconsecvente (ID-uri ca text sau număr, `""` în loc de null, obiect în loc de listă),
 * așa că citim fiecare element ca [JsonObject] și extragem câmpurile tolerant. Listele mari (zeci de MB)
 * se citesc în flux, element cu element.
 */
object XtreamJson {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun objects(input: InputStream): Sequence<JsonObject> =
        json
            .decodeToSequence<JsonElement>(input, DecodeSequenceMode.AUTO_DETECT)
            .flatMap { element ->
                when (element) {
                    is JsonObject -> sequenceOf(element)
                    is JsonArray -> element.asSequence().filterIsInstance<JsonObject>()
                    else -> emptySequence()
                }
            }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    fun movie(o: JsonObject): XtreamMovie? {
        val id = o.long("stream_id") ?: return null
        return XtreamMovie(
            streamId = id,
            name = o.str("name")?.trim().orEmpty(),
            categoryId = o.str("category_id"),
            icon = o.str("stream_icon"),
            containerExtension = o.str("container_extension"),
            tmdbId = (o.long("tmdb") ?: o.long("tmdb_id"))?.takeIf { it > 0 },
            added = o.long("added"),
        )
    }

    fun series(o: JsonObject): XtreamSeries? {
        val id = o.long("series_id") ?: return null
        return XtreamSeries(
            seriesId = id,
            name = o.str("name")?.trim().orEmpty(),
            categoryId = o.str("category_id"),
            cover = o.str("cover"),
            tmdbId = (o.long("tmdb") ?: o.long("tmdb_id"))?.takeIf { it > 0 },
            lastModified = o.long("last_modified"),
        )
    }

    /** `get_series_info`: `episodes` e de obicei `{"1": [...], "2": [...]}`, uneori o listă de liste. */
    fun episodes(info: JsonElement): List<XtreamEpisode> {
        val episodes = (info as? JsonObject)?.get("episodes") ?: return emptyList()
        val groups: List<Pair<Int?, JsonElement>> =
            when (episodes) {
                is JsonObject -> episodes.entries.map { (season, list) -> season.toIntOrNull() to list }
                is JsonArray -> episodes.map { null to it }
                else -> emptyList()
            }
        return groups.flatMap { (seasonKey, list) ->
            val items =
                when (list) {
                    is JsonArray -> list.filterIsInstance<JsonObject>()
                    is JsonObject -> list.values.filterIsInstance<JsonObject>()
                    else -> emptyList()
                }
            items.mapNotNull { e ->
                val id = e.long("id") ?: return@mapNotNull null
                XtreamEpisode(
                    id = id,
                    title = e.str("title")?.trim().orEmpty(),
                    season = e.long("season")?.toInt() ?: seasonKey,
                    episode = e.long("episode_num")?.toInt(),
                    containerExtension = e.str("container_extension"),
                    added = e.long("added"),
                )
            }
        }
    }

    /** `get_vod_categories` / `get_series_categories` → id → nume. */
    fun categories(element: JsonElement): Map<String, String> =
        (element as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { o ->
                val id = o.str("category_id") ?: return@mapNotNull null
                id to o.str("category_name")?.trim().orEmpty()
            }?.toMap()
            .orEmpty()

    internal fun JsonObject.str(key: String): String? {
        val value = this[key]
        if (value == null || value is JsonNull || value !is JsonPrimitive) return null
        return value.content.ifBlank { null }
    }

    internal fun JsonObject.long(key: String): Long? {
        val s = str(key)?.trim() ?: return null
        return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong()
    }
}
