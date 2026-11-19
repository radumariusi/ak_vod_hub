package io.github.radumariusi.akvodhub.data.diagnostic

import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.DiagnosticName
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.title.TitleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Cifrele scurte din Setări: câte surse nu au limbă sau calitate declarată. */
data class DiagnosticSummary(
    val total: Int,
    val noLanguage: Int,
    val noQuality: Int,
    /** Categoriile cele mai mari fără limbă (nume → câte surse). */
    val categoriesWithoutLanguage: List<Pair<String, Int>>,
)

/**
 * Diagnosticul identificării surselor: ce limbă și calitate a dedus parserul, pe categorii și pe un eșantion
 * de nume, plus metadatele tehnice (`info.video` / `info.audio`) pe care le trimite serverul Xtream.
 *
 * Exportul nu conține adrese, conturi sau parole: doar nume de categorii și de titluri.
 */
class SourceDiagnostics(
    private val db: AkVodDatabase,
    private val importer: PlaylistImporter,
    private val xtream: XtreamClient,
) {
    private val dao get() = db.playlistDao()

    suspend fun summary(): DiagnosticSummary =
        withContext(Dispatchers.IO) {
            val counts = dao.diagnosticCounts().filter { it.kind != VodKind.EPISODE || it.total > 0 }
            val categories =
                dao
                    .diagnosticCategories(CATEGORY_LIMIT)
                    .filter { it.noLanguage * 2 > it.total }
                    .take(20)
                    .map { it.groupTitle to it.total }
            DiagnosticSummary(
                total = counts.sumOf { it.total },
                noLanguage = counts.sumOf { it.noLanguage },
                noQuality = counts.sumOf { it.noQuality },
                categoriesWithoutLanguage = categories,
            )
        }

    suspend fun report(): String =
        withContext(Dispatchers.IO) {
            buildString {
                appendLine("AK VOD Hub · diagnostic surse")
                appendLine("Fără adrese, conturi sau parole: doar nume de categorii și titluri.")
                appendLine()
                appendLine("== Totaluri (tip · surse · fără limbă · fără calitate)")
                dao.diagnosticCounts().forEach {
                    appendLine("${it.kind}\t${it.total}\t${it.noLanguage}\t${it.noQuality}")
                }
                appendLine()
                appendLine("== Categorii (nume · tip · surse · fără limbă · limbă dedusă · calitate dedusă)")
                dao.diagnosticCategories(CATEGORY_LIMIT).forEach {
                    val parsed = TitleParser.parse("X", it.groupTitle)
                    appendLine(
                        listOf(
                            clean(it.groupTitle),
                            it.kind.name,
                            it.total,
                            it.noLanguage,
                            parsed.languages.joinToString("+").ifEmpty { "-" },
                            parsed.quality?.name ?: "-",
                        ).joinToString("\t"),
                    )
                }
                val movies = dao.diagnosticNames(VodKind.MOVIE, NAME_LIMIT)
                val series = dao.diagnosticNames(VodKind.SERIES, NAME_LIMIT / 2)
                val episodes = dao.diagnosticNames(VodKind.EPISODE, NAME_LIMIT / 2)
                for ((label, list) in listOf("Filme" to movies, "Seriale" to series, "Episoade" to episodes)) {
                    appendLine()
                    appendLine("== $label (nume · categorie · titlu · an · S/E · limbi audio · subtitrări · calitate)")
                    list.forEach { appendLine(nameLine(it)) }
                }
                appendLine()
                appendLine("== Metadate Xtream (info.video / info.audio)")
                appendXtream(movies.firstOrNull { it.playlistType == PlaylistType.XTREAM }, "get_vod_info", "vod_id")
                appendXtream(
                    series.firstOrNull { it.playlistType == PlaylistType.XTREAM },
                    "get_series_info",
                    "series_id",
                )
            }
        }

    private fun nameLine(entry: DiagnosticName): String {
        val p = TitleParser.parse(entry.name, entry.groupTitle)
        return listOf(
            clean(entry.name),
            clean(entry.groupTitle.orEmpty()),
            p.title,
            p.year ?: "",
            if (p.season != null || p.episode != null) "S${p.season ?: "?"}E${p.episode ?: "?"}" else "",
            entry.languages ?: "-",
            p.subtitles.joinToString("+").ifEmpty { "-" },
            entry.quality?.name ?: "-",
        ).joinToString("\t")
    }

    private suspend fun StringBuilder.appendXtream(entry: DiagnosticName?, action: String, idName: String) {
        val id = entry?.streamId ?: return
        appendLine("-- $action pentru „${clean(entry.name)}”")
        try {
            val source = dao.sources().firstOrNull { it.id == entry.sourceId } ?: return
            val info =
                withTimeoutOrNull(XTREAM_TIMEOUT_MS) { xtream.rawInfo(importer.account(source), action, idName, id) }
            appendLine(info?.let(::mediaOnly)?.toString() ?: "fără răspuns în ${XTREAM_TIMEOUT_MS / 1000} s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appendLine("eroare: ${e.message.orEmpty().take(200)}")
        }
    }

    /** Doar câmpurile tehnice: `info.video`, `info.audio`, `info.bitrate`; la seriale, primele 3 episoade. */
    private fun mediaOnly(element: JsonElement): JsonElement {
        val root = element as? JsonObject ?: return JsonObject(emptyMap())

        fun technical(info: JsonElement?): JsonObject {
            val o = info as? JsonObject ?: return JsonObject(emptyMap())
            return buildJsonObject {
                listOf("video", "audio", "bitrate", "duration").forEach { key -> o[key]?.let { put(key, it) } }
            }
        }
        val episodes = root["episodes"]
        if (episodes == null) return buildJsonObject { put("info", technical(root["info"])) }
        val list =
            when (episodes) {
                is JsonObject -> episodes.values.flatMap { (it as? JsonArray).orEmpty() }
                is JsonArray -> episodes.flatMap { (it as? JsonArray) ?: listOf(it) }
                else -> emptyList()
            }
        return JsonArray(
            list.take(3).map { e ->
                val o = e as? JsonObject ?: JsonObject(emptyMap())
                buildJsonObject {
                    o["season"]?.let { put("season", it) }
                    o["episode_num"]?.let { put("episode_num", it) }
                    put("info", technical(o["info"]))
                }
            },
        )
    }

    private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').take(200)

    private companion object {
        const val CATEGORY_LIMIT = 400
        const val NAME_LIMIT = 300
        const val XTREAM_TIMEOUT_MS = 15_000L
    }
}
