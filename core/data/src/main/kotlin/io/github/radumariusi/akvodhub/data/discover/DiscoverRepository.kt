package io.github.radumariusi.akvodhub.data.discover

import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.DISCOVER_COLUMNS
import io.github.radumariusi.akvodhub.data.db.DiscoverCacheEntity
import io.github.radumariusi.akvodhub.data.db.DiscoverTitle
import io.github.radumariusi.akvodhub.data.db.HistoryTitle
import io.github.radumariusi.akvodhub.data.db.ImdbMapEntity
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.data.stremio.StremioAddon
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.data.stremio.StremioCatalog
import io.github.radumariusi.akvodhub.data.stremio.StremioClient
import io.github.radumariusi.akvodhub.data.stremio.StremioManifest
import io.github.radumariusi.akvodhub.data.stremio.StremioMeta
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.discover.Candidate
import io.github.radumariusi.akvodhub.parser.discover.CollectionPart
import io.github.radumariusi.akvodhub.parser.discover.Collections
import io.github.radumariusi.akvodhub.parser.discover.HistoryItem
import io.github.radumariusi.akvodhub.parser.discover.PersonWeight
import io.github.radumariusi.akvodhub.parser.discover.Recommender
import io.github.radumariusi.akvodhub.parser.discover.SignalsBuilder
import io.github.radumariusi.akvodhub.parser.discover.TitleRef
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.TitleSimilarity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.Calendar
import java.util.Collections as JavaCollections

/** Un rând de descoperire (Acasă sau Detalii): doar titluri care există în playlist-uri. */
data class DiscoverRow(
    val key: String,
    val title: String,
    val items: List<TitleCard>,
)

/** Colecția (francize) unui film: toate părțile, cu cele disponibile marcate. */
data class CollectionInfo(
    val name: String,
    val parts: List<CollectionPart>,
    /** Ex. „Ai 2 din 3 părți · lipsește: …”. */
    val summary: String,
    /** Părțile disponibile, în ordine (inclusiv titlul deschis). */
    val available: List<TitleCard>,
)

/** Ce arată pagina de detalii sub surse: colecția, recomandate, similare, alte titluri ale regizorului. */
data class TitleDiscovery(
    val collection: CollectionInfo?,
    val rows: List<DiscoverRow>,
)

/**
 * Descoperire și recomandări, întotdeauna limitate la ce există în playlist-uri:
 * - pe Detalii: colecția, „Recomandate” și „Similare” de la TMDB, alte titluri ale regizorului;
 * - pe Acasă: „Recomandate pentru tine” (scor local din istoric: genuri, regizori, actori, an), „Pentru că ai
 *   văzut X”, „Continuă colecția” și cataloagele / listele din addon-urile Stremio.
 *
 * Listele TMDB se țin în baza locală (`discover_cache`), deci se cer o dată la câteva săptămâni; fără cheie TMDB
 * rămân doar recomandările locale (după genuri și an) și cataloagele addon-urilor.
 */
class DiscoverRepository(
    db: AkVodDatabase,
    private val tmdb: TmdbClient,
    private val settings: TmdbSettings,
    private val stremio: StremioClient,
    private val addons: StremioAddons,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.discoverDao()
    private val json = DiscoverCache.json
    private val manifests = JavaCollections.synchronizedMap(HashMap<String, StremioManifest>())
    private val catalogLock = Mutex()

    /** Addon-urile instalate (cataloagele se reîncarcă la schimbare). */
    val installedAddons: StateFlow<List<StremioAddon>> get() = addons.addons

    /** TMDB primește cel mult câteva cereri de aici în paralel (potrivirea poate rula în același timp). */
    private val tmdbPermits = Semaphore(TMDB_PARALLEL)

    // ------------------------------------------------------------ detalii

    suspend fun forTitle(title: TitleEntity): TitleDiscovery? {
        if (title.isLocal) return null
        val key = settings.apiKey() ?: return null
        val related = related(key, title.mediaType, title.tmdbId) ?: return null
        val rows = mutableListOf<DiscoverRow>()

        val collection = related.collectionId?.let { collectionInfo(key, it) }
        val inCollection =
            collection
                ?.parts
                ?.mapNotNull { it.titleId }
                .orEmpty()
                .toSet()
        val exclude = inCollection + title.id

        val recommended = available(related.recommendations.map { it.ref }).filter { it.id !in exclude }
        if (recommended.isNotEmpty()) rows += DiscoverRow("recommended", "Recomandate", recommended.map { it.card() })
        val shown = exclude + recommended.map { it.id }
        val similar = available(related.similar.map { it.ref }).filter { it.id !in shown }
        if (similar.isNotEmpty()) rows += DiscoverRow("similar", "Similare", similar.map { it.card() })

        val director = related.people.firstOrNull { it.director }
        if (director != null) {
            val films = filmography(key, director.id)
            val directed = available(films?.directed.orEmpty().map { it.ref }).filter { it.id != title.id }
            if (directed.isNotEmpty()) {
                rows += DiscoverRow("director", "Mai multe de la ${director.name}", directed.map { it.card() })
            }
        }
        return TitleDiscovery(collection?.takeIf { it.parts.size > 1 }, rows)
    }

    private suspend fun collectionInfo(
        key: String,
        collectionId: Long,
    ): CollectionInfo? {
        val c = collection(key, collectionId) ?: return null
        val found = available(c.parts.map { it.ref }).associateBy { it.tmdbId }
        val parts =
            Collections.ordered(
                c.parts.map { CollectionPart(it.id, it.name, it.year, found[it.id]?.id) },
                maxYear = currentYear() + 1,
            )
        if (parts.isEmpty()) return null
        val available = parts.mapNotNull { p -> found[p.tmdbId]?.card() }
        return CollectionInfo(c.name, parts, Collections.summary(parts), available)
    }

    // ------------------------------------------------------------ Acasă: recomandări personale

    /**
     * Rândurile personalizate de pe Acasă. Gol dacă nu s-a văzut nimic încă. Doar titluri cu surse, fără cele
     * deja văzute sau începute.
     */
    suspend fun personalRows(): List<DiscoverRow> {
        val now = clock()
        val history = dao.recentHistory(MAX_HISTORY)
        if (history.isEmpty()) return emptyList()
        val items = history.map { it.toItem() }
        val inProgress = dao.inProgress().toSet()
        val seen = history.map { it.titleId }.toSet() + inProgress
        val key = settings.apiKey()

        // Titlurile „sămânță”: cele mai importante din istoric (recente și văzute mult), cu potrivire TMDB.
        val weights = items.associate { it.titleId to Recommender.weight(it, now) }
        val seeds =
            history
                .filter { it.tmdbId > 0 }
                .sortedByDescending { weights[it.titleId] ?: 0.0 }
                .take(MAX_SEEDS)
        val related: Map<Long, CachedRelated> =
            if (key == null) {
                emptyMap()
            } else {
                coroutineScope {
                    seeds
                        .map { s -> async { s.titleId to related(key, s.mediaType, s.tmdbId) } }
                        .awaitAll()
                        .mapNotNull { (id, r) -> r?.let { id to it } }
                        .toMap()
                }
            }
        val people =
            related.mapValues { (_, r) ->
                r.people.mapIndexed { i, p ->
                    PersonWeight(
                        p.id,
                        if (p.director) {
                            DIRECTOR_WEIGHT
                        } else if (i < 3) {
                            LEAD_WEIGHT
                        } else {
                            CAST_WEIGHT
                        },
                    )
                }
            }
        val profile = Recommender.profile(items, people, now)
        val signals = SignalsBuilder()
        val refs = LinkedHashMap<TitleRef, MutableList<(Long) -> Unit>>()

        fun hook(
            ref: TitleRef,
            action: (Long) -> Unit,
        ) {
            refs.getOrPut(ref) { mutableListOf() } += action
        }

        for ((seedId, r) in related) {
            val w = weights[seedId] ?: continue
            r.recommendations.forEachIndexed { rank, c -> hook(c.ref) { signals.related(it, rank, w) } }
            r.similar.forEachIndexed { rank, c -> hook(c.ref) { signals.related(it, rank, w * SIMILAR_FACTOR) } }
        }
        // Colecțiile titlurilor văzute și ce urmează în ele.
        val collections =
            if (key == null) {
                emptyList()
            } else {
                related.values
                    .mapNotNull { it.collectionId }
                    .distinct()
                    .mapNotNull { collection(key, it) }
            }
        collections.forEach { c -> c.parts.forEach { p -> hook(p.ref) { signals.collection(it) } } }
        // Filmografia persoanelor preferate (regizori, actori principali).
        if (key != null) {
            val top = profile.topPeople(MAX_PEOPLE)
            val films =
                coroutineScope { top.map { p -> async { p to filmography(key, p) } }.awaitAll() }
            for ((person, f) in films) {
                if (f == null) continue
                (f.directed + f.acted.take(MAX_ACTED)).forEach { c -> hook(c.ref) { signals.person(it, person) } }
            }
        }

        val resolved = available(refs.keys.toList())
        for (t in resolved) refs[TitleRef(t.mediaType, t.tmdbId)]?.forEach { it(t.id) }
        val pool = LinkedHashMap<Long, DiscoverTitle>()
        resolved.forEach { pool[it.id] = it }
        // Plus cele mai populare titluri din genurile preferate (merge și fără cheie TMDB).
        for (genre in profile.topGenres(MAX_GENRES)) {
            dao.popularInGenre("%,$genre,%", GENRE_POOL).forEach { pool.getOrPut(it.id) { it } }
        }

        val rows = mutableListOf<DiscoverRow>()
        val built = signals.build()
        val forYou = Recommender.rank(profile, pool.values.map { it.candidate() }, built, seen, ROW_SIZE)
        if (forYou.size >= MIN_ROW) {
            rows += DiscoverRow("for-you", "Recomandate pentru tine", forYou.mapNotNull { pool[it]?.card() })
        }

        // „Continuă colecția”: partea următoare (disponibilă, nevăzută) din colecțiile începute.
        val done = history.filter { it.finished || it.progress >= DONE_PROGRESS }.map { it.titleId }.toSet()
        val next =
            collections.mapNotNull { c ->
                val found = available(c.parts.map { it.ref }).associateBy { it.tmdbId }
                val parts =
                    Collections.ordered(
                        c.parts.map { CollectionPart(it.id, it.name, it.year, found[it.id]?.id) },
                        maxYear = currentYear() + 1,
                    )
                Collections.next(parts, done)?.let { found[it.tmdbId] }?.takeIf { it.id !in inProgress }
            }
        if (next.isNotEmpty()) {
            rows += DiscoverRow("collections", "Continuă colecția", next.distinctBy { it.id }.map { it.card() })
        }

        // „Pentru că ai văzut X”: cele mai recente titluri văzute serios, fiecare cu lista lui.
        val because =
            history
                .filter { it.titleId in related && (it.finished || it.progress >= BECAUSE_PROGRESS) }
                .take(MAX_BECAUSE)
        val used = forYou.take(BECAUSE_SKIP).toMutableSet()
        for (seed in because) {
            val r = related[seed.titleId] ?: continue
            val single =
                Recommender.profile(
                    listOf(seed.toItem()),
                    mapOf(seed.titleId to people[seed.titleId].orEmpty()),
                    now,
                )
            val s = SignalsBuilder()
            val own = LinkedHashMap<TitleRef, MutableList<(Long) -> Unit>>()
            r.recommendations.forEachIndexed { rank, c ->
                own.getOrPut(c.ref) { mutableListOf() } +=
                    { s.related(it, rank, 1.0) }
            }
            r.similar.forEachIndexed { rank, c ->
                own.getOrPut(c.ref) { mutableListOf() } += { s.related(it, rank, SIMILAR_FACTOR) }
            }
            val found = available(own.keys.toList())
            found.forEach { t -> own[TitleRef(t.mediaType, t.tmdbId)]?.forEach { it(t.id) } }
            val byId = found.associateBy { it.id }
            val ranked = Recommender.rank(single, found.map { it.candidate() }, s.build(), seen + used, ROW_SIZE)
            if (ranked.size >= MIN_ROW) {
                used += ranked.take(BECAUSE_SKIP)
                rows +=
                    DiscoverRow(
                        "because:${seed.titleId}",
                        "Pentru că ai văzut ${seed.name}",
                        ranked.mapNotNull { byId[it]?.card() },
                    )
            }
        }
        return rows
    }

    // ------------------------------------------------------------ Acasă: cataloagele addon-urilor

    /**
     * Câte un rând pentru fiecare catalog (filme / seriale) din addon-urile instalate, cu titlurile găsite în
     * playlist, în ordinea din catalog. Cataloagele se descarcă cel mult o dată la [CATALOG_TTL_MS].
     */
    suspend fun catalogRows(): List<DiscoverRow> =
        catalogLock.withLock {
            val installed = addons.addons.value.filter { it.resources.isEmpty() || "catalog" in it.resources }
            if (installed.isEmpty()) return emptyList()
            dao.deleteCacheBefore(clock() - CACHE_CLEANUP_MS)
            val key = settings.apiKey()
            val catalogs =
                coroutineScope {
                    installed
                        .map { addon ->
                            async {
                                val manifest = manifest(addon) ?: return@async emptyList()
                                manifest.catalogs
                                    .filter { it.browsable }
                                    .take(MAX_CATALOGS_PER_ADDON)
                                    .map { addon to it }
                            }
                        }.awaitAll()
                        .flatten()
                }
            val metas =
                coroutineScope {
                    catalogs.map { (addon, catalog) -> async { catalogMetas(addon, catalog) } }.awaitAll()
                }
            var lookups = MAX_IMDB_LOOKUPS
            val rows = mutableListOf<DiscoverRow>()
            for ((index, pair) in catalogs.withIndex()) {
                val (addon, catalog) = pair
                val list = metas[index] ?: continue
                val (titles, used) = resolveMetas(list, key, lookups)
                lookups -= used
                if (titles.isEmpty()) continue
                val type = if (catalog.type == "series") "Seriale" else "Filme"
                rows +=
                    DiscoverRow(
                        key = "catalog:${addon.manifestUrl}|${catalog.type}|${catalog.id}",
                        title = "${catalog.name} · $type · ${addon.name}",
                        items = titles.map { it.card() },
                    )
            }
            rows
        }

    private suspend fun manifest(addon: StremioAddon): StremioManifest? {
        manifests[addon.manifestUrl]?.let { return it }
        return safely { stremio.manifest(addon.manifestUrl) }?.also { manifests[addon.manifestUrl] = it }
    }

    /** Titlurile catalogului (până la [CATALOG_PAGES] pagini), din cache dacă e proaspăt. */
    private suspend fun catalogMetas(
        addon: StremioAddon,
        catalog: StremioCatalog,
    ): List<CachedMeta>? {
        val cacheKey = "cat:${addon.manifestUrl}|${catalog.type}|${catalog.id}"
        val cached = dao.cached(cacheKey)
        val fresh = cached != null && clock() - cached.fetchedAt < CATALOG_TTL_MS
        if (fresh) decode<CachedCatalog>(cached!!.data)?.let { return it.metas }
        val all = mutableListOf<StremioMeta>()
        var pages = 0
        var page = safely { stremio.catalog(addon, catalog) }
        while (!page.isNullOrEmpty()) {
            all += page
            pages++
            if (!catalog.skip || pages >= CATALOG_PAGES || all.size >= MAX_CATALOG_ITEMS) break
            page = safely { stremio.catalog(addon, catalog, skip = all.size) }
        }
        if (all.isEmpty()) {
            // Addon-ul nu răspunde: folosim ce aveam, chiar dacă e vechi.
            return cached?.let { decode<CachedCatalog>(it.data)?.metas }
        }
        val metas = all.distinctBy { it.id }.map { DiscoverCache.meta(it, catalog.type) }
        dao.putCache(DiscoverCacheEntity(cacheKey, json.encodeToString(CachedCatalog(metas)), clock()))
        return metas
    }

    /**
     * Găsește titlurile din catalog în playlist: după ID-ul TMDB, după IMDb (din fișă sau din legăturile aflate),
     * după nume + an, iar la nevoie întreabă TMDB (`/find`, cel mult [budget] cereri). Întoarce titlurile în
     * ordinea catalogului și câte cereri TMDB s-au folosit.
     */
    private suspend fun resolveMetas(
        metas: List<CachedMeta>,
        key: String?,
        budget: Int,
    ): Pair<List<DiscoverTitle>, Int> {
        val result = arrayOfNulls<DiscoverTitle>(metas.size)

        fun type(m: CachedMeta) = if (m.tv) MediaType.TV else MediaType.MOVIE

        // 1. ID TMDB direct.
        val tmdbRefs = metas.mapNotNull { m -> m.tmdb?.let { TitleRef(type(m), it) } }
        val byTmdb = available(tmdbRefs).associateBy { TitleRef(it.mediaType, it.tmdbId) }
        metas.forEachIndexed { i, m -> m.tmdb?.let { result[i] = byTmdb[TitleRef(type(m), it)] } }

        // 2. IMDb: din fișa titlului, apoi din legăturile IMDb → TMDB știute.
        val imdbs =
            metas.indices
                .filter { result[it] == null }
                .mapNotNull { metas[it].imdb }
                .distinct()
        val byImdb = imdbs.chunked(CHUNK).flatMap { dao.availableByImdb(it) }.associateBy { it.imdbId }
        val known = imdbs.chunked(CHUNK).flatMap { dao.imdbMap(it) }.associateBy { it.imdbId }
        val viaMap = available(known.values.mapNotNull { it.ref() }).associateBy { TitleRef(it.mediaType, it.tmdbId) }
        metas.forEachIndexed { i, m ->
            if (result[i] == null && m.imdb != null) {
                result[i] = byImdb[m.imdb] ?: known[m.imdb]?.ref()?.let { viaMap[it] }
            }
        }

        // 3. Nume + an (fără rețea).
        metas.forEachIndexed { i, m -> if (result[i] == null) result[i] = byName(m.name, m.year, type(m)) }

        // 4. TMDB /find pentru ce a rămas (doar IMDb-uri neverificate sau verificate demult).
        var used = 0
        if (key != null) {
            val now = clock()
            val pending =
                metas.indices
                    .filter { result[it] == null }
                    .mapNotNull { i -> metas[i].imdb?.let { i to it } }
                    .filter { (_, imdb) -> known[imdb]?.let { now - it.checkedAt > IMDB_RECHECK_MS } ?: true }
                    .distinctBy { it.second }
                    .take(budget)
            used = pending.size
            val found =
                coroutineScope {
                    pending
                        .map { (i, imdb) ->
                            async { Triple(i, imdb, tmdbCall { tmdb.findImdb(key, imdb) }) }
                        }.awaitAll()
                }
            val entries =
                found.mapNotNull { (i, imdb, r) ->
                    if (r == null) return@mapNotNull null
                    // Întâi tipul din catalog, apoi celălalt (unele addon-uri amestecă tipurile).
                    val movie = r.movies.firstOrNull()?.let { TitleRef(MediaType.MOVIE, it.id) }
                    val show = r.tv.firstOrNull()?.let { TitleRef(MediaType.TV, it.id) }
                    val hit = if (metas[i].tv) show ?: movie else movie ?: show
                    ImdbMapEntity(imdb, hit?.type, hit?.tmdbId, now)
                }
            if (entries.isNotEmpty()) dao.putImdbMap(entries)
            val titles = available(entries.mapNotNull { it.ref() }).associateBy { TitleRef(it.mediaType, it.tmdbId) }
            val byImdbId = entries.associateBy { it.imdbId }
            metas.forEachIndexed { i, m ->
                if (result[i] == null && m.imdb != null) {
                    result[i] = byImdbId[m.imdb]?.ref()?.let { titles[it] }
                }
            }
        }
        return result.filterNotNull().distinctBy { it.id } to used
    }

    /** Titlul cu exact același nume (normalizat) și an apropiat, căutat în indexul FTS. */
    private suspend fun byName(
        name: String,
        year: Int?,
        type: MediaType,
    ): DiscoverTitle? {
        val wanted = TitleSimilarity.normalize(name)
        val words = wanted.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val match = "\"" + words.joinToString(" ") + "\""
        val rows =
            dao.titles(
                SimpleSQLiteQuery(
                    "SELECT $DISCOVER_COLUMNS FROM title t WHERE t.sourceCount > 0 AND t.mediaType = ? AND t.id IN " +
                        "(SELECT docid FROM title_search WHERE title_search MATCH ?) LIMIT 40",
                    arrayOf(type.name, match),
                ),
            )
        return rows
            .filter { t ->
                (
                    TitleSimilarity.normalize(t.name) == wanted ||
                        t.originalName?.let { TitleSimilarity.normalize(it) } == wanted
                ) &&
                    (year == null || t.year == null || kotlin.math.abs(t.year - year) <= 1)
            }.maxByOrNull { it.popularity ?: 0.0 }
    }

    // ------------------------------------------------------------ TMDB, cu cache

    private suspend fun related(
        key: String,
        type: MediaType,
        tmdbId: Long,
    ): CachedRelated? =
        cachedOrFetch("rel:${type.name}:$tmdbId", RELATED_TTL_MS) {
            tmdbCall { tmdb.related(key, type, tmdbId) }?.let { DiscoverCache.related(it, type == MediaType.TV) }
        }

    private suspend fun collection(
        key: String,
        collectionId: Long,
    ): CachedCollection? =
        cachedOrFetch("col:$collectionId", COLLECTION_TTL_MS) {
            tmdbCall { tmdb.collection(key, collectionId) }?.let { DiscoverCache.collection(it) }
        }

    private suspend fun filmography(
        key: String,
        personId: Long,
    ): CachedFilmography? =
        cachedOrFetch("person:$personId", PERSON_TTL_MS) {
            tmdbCall { tmdb.personCredits(key, personId) }?.let { DiscoverCache.filmography(it) }
        }

    private suspend inline fun <reified T> cachedOrFetch(
        cacheKey: String,
        ttl: Long,
        fetch: () -> T?,
    ): T? {
        val cached = dao.cached(cacheKey)
        if (cached != null && clock() - cached.fetchedAt < ttl) decode<T>(cached.data)?.let { return it }
        val fresh = fetch()
        if (fresh == null) return cached?.let { decode<T>(it.data) }
        dao.putCache(DiscoverCacheEntity(cacheKey, json.encodeToString(fresh), clock()))
        return fresh
    }

    private inline fun <reified T> decode(data: String): T? = runCatching { json.decodeFromString<T>(data) }.getOrNull()

    private suspend fun <T> tmdbCall(block: suspend () -> T?): T? = tmdbPermits.withPermit { safely { block() } }

    private suspend fun <T> safely(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // ------------------------------------------------------------ titluri disponibile

    /** Titlurile din [refs] care există în playlist-uri, în ordinea din [refs]. */
    private suspend fun available(refs: List<TitleRef>): List<DiscoverTitle> {
        if (refs.isEmpty()) return emptyList()
        val found = HashMap<TitleRef, DiscoverTitle>()
        refs.distinct().groupBy { it.type }.forEach { (type, list) ->
            list.map { it.tmdbId }.chunked(CHUNK).forEach { ids ->
                dao.availableByTmdb(type, ids).forEach { found[TitleRef(it.mediaType, it.tmdbId)] = it }
            }
        }
        return refs.mapNotNull { found[it] }.distinctBy { it.id }
    }

    private fun currentYear() = Calendar.getInstance().apply { timeInMillis = clock() }.get(Calendar.YEAR)

    private companion object {
        const val DAY_MS = 86_400_000L
        const val RELATED_TTL_MS = 21 * DAY_MS
        const val COLLECTION_TTL_MS = 30 * DAY_MS
        const val PERSON_TTL_MS = 30 * DAY_MS
        const val CATALOG_TTL_MS = 6 * 3_600_000L
        const val CACHE_CLEANUP_MS = 90 * DAY_MS
        const val IMDB_RECHECK_MS = 30 * DAY_MS

        const val TMDB_PARALLEL = 4
        const val CHUNK = 400
        const val MAX_HISTORY = 80
        const val MAX_SEEDS = 6
        const val MAX_PEOPLE = 5
        const val MAX_ACTED = 40
        const val MAX_GENRES = 3
        const val GENRE_POOL = 150
        const val ROW_SIZE = 30
        const val MIN_ROW = 3
        const val MAX_BECAUSE = 2
        const val BECAUSE_SKIP = 8
        const val BECAUSE_PROGRESS = 0.3
        const val DONE_PROGRESS = 0.9
        const val SIMILAR_FACTOR = 0.7
        const val DIRECTOR_WEIGHT = 1.0
        const val LEAD_WEIGHT = 0.6
        const val CAST_WEIGHT = 0.3

        const val MAX_CATALOGS_PER_ADDON = 12
        const val MAX_CATALOG_ITEMS = 300
        const val CATALOG_PAGES = 3
        const val MAX_IMDB_LOOKUPS = 150

        fun ImdbMapEntity.ref(): TitleRef? {
            val type = mediaType ?: return null
            val id = tmdbId ?: return null
            return TitleRef(type, id)
        }

        fun HistoryTitle.toItem() =
            HistoryItem(
                titleId = titleId,
                type = mediaType,
                genreIds = genres(genreIds),
                year = year,
                progress = progress,
                finished = finished,
                watchedAt = watchedAt,
            )

        fun DiscoverTitle.candidate() =
            Candidate(id, mediaType, genres(genreIds), year, rating, voteCount, popularity)

        fun genres(csv: String?): Set<Int> =
            csv
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                .orEmpty()
    }
}
