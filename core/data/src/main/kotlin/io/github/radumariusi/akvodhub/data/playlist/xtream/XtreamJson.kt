package io.github.radumariusi.akvodhub.data.playlist.xtream

import io.github.radumariusi.akvodhub.parser.playlist.JsonObjectStream
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import io.github.radumariusi.akvodhub.parser.title.VideoResolution
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    fun objects(input: InputStream): Sequence<JsonObject> =
        JsonObjectStream.objects(input.bufferedReader(Charsets.UTF_8)).map { parse(it) as JsonObject }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text.removePrefix("\uFEFF"))

    fun movie(o: JsonObject): XtreamMovie? {
        val id = o.long("stream_id")?.takeIf { it > 0 } ?: return null
        return XtreamMovie(
            streamId = id,
            name = o.str("name")?.trim().orEmpty(),
            categoryId = o.str("category_id"),
            icon = o.str("stream_icon"),
            containerExtension = o.str("container_extension"),
            tmdbId = (o.long("tmdb") ?: o.long("tmdb_id"))?.takeIf { it > 0 },
            added = o.long("added"),
            quality = o.declaredQuality(),
        )
    }

    fun series(o: JsonObject): XtreamSeries? {
        val id = o.long("series_id")?.takeIf { it > 0 } ?: return null
        return XtreamSeries(
            seriesId = id,
            name = o.str("name")?.trim().orEmpty(),
            categoryId = o.str("category_id"),
            cover = o.str("cover"),
            tmdbId = (o.long("tmdb") ?: o.long("tmdb_id"))?.takeIf { it > 0 },
            lastModified = o.long("last_modified"),
            quality = o.declaredQuality(),
        )
    }

    /** `get_series_info`: `episodes` e de obicei `{"1": [...], "2": [...]}`, uneori o listă de liste. */
    fun episodes(info: JsonElement): List<XtreamEpisode> {
        val episodes = (info as? JsonObject)?.get("episodes") ?: throw XtreamException("Lipsește lista de episoade.")
        val groups =
            when (episodes) {
                is JsonObject -> episodes.entries.map { (season, list) -> season.toIntOrNull() to list }
                is JsonArray -> episodes.map { null to it }
                else -> throw XtreamException("Lista de episoade are format invalid.")
            }
        return groups.flatMap { (seasonKey, group) ->
            val items =
                when (group) {
                    is JsonArray -> group
                    is JsonObject -> if ("id" in group) listOf(group) else group.values
                    else -> throw XtreamException("Grup de episoade invalid.")
                }
            items.map { raw ->
                val e = raw as? JsonObject ?: throw XtreamException("Episod cu format invalid.")
                val id = e.long("id") ?: throw XtreamException("Episod fără ID valid.")
                XtreamEpisode(
                    id = id,
                    title = e.str("title")?.trim().orEmpty(),
                    season = e.long("season")?.toInt() ?: seasonKey,
                    episode = e.long("episode_num")?.toInt(),
                    containerExtension = e.str("container_extension"),
                    added = e.long("added"),
                    quality = e.declaredQuality(),
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

    private fun JsonObject.declaredQuality(): VideoQuality? {
        val video = this["video"] as? JsonObject ?: (this["info"] as? JsonObject)?.get("video") as? JsonObject
        for (data in listOfNotNull(video, this)) {
            val width = data.str("width")?.toIntOrNull()
            val height = data.str("height")?.toIntOrNull()
            if (width != null && height != null) VideoResolution.quality(width, height)?.let { return it }
            VideoResolution.declared(data.str("resolution"))?.let { return it }
            VideoResolution.declared(data.str("quality"))?.let { return it }
        }
        return null
    }

    internal fun JsonObject.str(key: String): String? {
        val value = this[key]
        if (value == null || value is JsonNull || value !is JsonPrimitive) return null
        return value.content.ifBlank { null }
    }

    internal fun JsonObject.long(key: String): Long? {
        val s = str(key)?.trim() ?: return null
        return s.toLongOrNull() ?: try {
            s.toBigDecimal().longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }
}
