package io.github.radumariusi.akvodhub.data.catalog

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.DiscoverCacheEntity
import io.github.radumariusi.akvodhub.data.db.PersonCreditEntity
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPerson
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.catalog.ActorFilmography
import io.github.radumariusi.akvodhub.parser.catalog.FilmographyCredit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class ActorRepository(
    private val db: AkVodDatabase,
    private val tmdb: TmdbClient,
    private val settings: TmdbSettings,
) {
    private val mutex = Mutex()

    suspend fun prepare(personId: Long) =
        mutex.withLock {
            require(personId > 0)
            val cache = db.discoverDao()
            val key = "actor:$personId"
            val old = cache.cached(key)
            val now = System.currentTimeMillis()
            if (old != null && now >= old.fetchedAt && now - old.fetchedAt < TTL) return@withLock
            try {
                val apiKey = settings.apiKey() ?: throw IOException("Cheia TMDB lipsește.")
                val result =
                    tmdb.personCredits(apiKey, personId) ?: throw IOException("Filmografia nu este disponibilă.")
                val references = ActorFilmography.titles(result.cast.map { FilmographyCredit(it.id, it.mediaType) })
                db.withTransaction {
                    db.catalogDao().clearPersonCredits(personId)
                    references.chunked(500).forEach { batch ->
                        db.catalogDao().putPersonCredits(batch.map { PersonCreditEntity(personId, it.type, it.tmdbId) })
                    }
                    cache.putCache(DiscoverCacheEntity(key, "{}", now))
                    db.catalogDao().trimPersonCredits()
                    db.catalogDao().trimPersonCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (old == null && !db.catalogDao().hasPersonCredits(personId)) throw e
                // Offline/eroare: filmografia din cache rămâne utilă; nu modificăm titlurile sau sursele.
            }
        }

    private val profiles = java.util.concurrent.ConcurrentHashMap<Long, TmdbPerson>()

    /** Biografia și datele persoanei (ținute în memorie). null = fără cheie, offline sau inexistentă. */
    suspend fun profile(personId: Long): TmdbPerson? {
        if (personId <= 0) return null
        profiles[personId]?.let { return it }
        val apiKey = settings.apiKey() ?: return null
        val person =
            try {
                tmdb.person(apiKey, personId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null
        profiles[personId] = person
        return person
    }

    suspend fun page(personId: Long, afterId: Long, limit: Int): List<TitleCard> {
        val query = ActorFilmography.page(personId, afterId, limit)
        return db.catalogDao().cards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    private companion object {
        const val TTL = 21L * 24 * 60 * 60 * 1000
    }
}
