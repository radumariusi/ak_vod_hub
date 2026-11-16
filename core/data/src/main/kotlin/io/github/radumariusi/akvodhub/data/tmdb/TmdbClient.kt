package io.github.radumariusi.akvodhub.data.tmdb

import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.SearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Client TMDB (API v3), cu cheia utilizatorului: acceptă atât „API Key” (32 de caractere) cât și
 * „API Read Access Token” (începe cu `eyJ`).
 *
 * Limitare de rată: TMDB acceptă ~50 de cereri/s de pe un IP; noi trimitem cel mult [requestsPerSecond]
 * și respectăm `Retry-After` la HTTP 429. Erorile de rețea și 5xx se reîncearcă de câteva ori.
 */
class TmdbClient(
    private val http: OkHttpClient,
    requestsPerSecond: Int = 30,
    private val baseUrl: String = "https://api.themoviedb.org/3",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val limiter = RateLimiter(requestsPerSecond, clock)

    /** Verifică dacă cheia e acceptată de TMDB. */
    suspend fun validate(key: String): Boolean =
        try {
            get(key, url("configuration").build())
            true
        } catch (e: TmdbAuthException) {
            false
        }

    suspend fun search(
        key: String,
        request: SearchRequest,
    ): List<TmdbItem> {
        val tv = request.type == MediaType.TV
        val url =
            url(if (tv) "search/tv" else "search/movie")
                .addQueryParameter("query", request.query)
                .addQueryParameter("include_adult", "false")
                .addQueryParameter("language", request.language ?: METADATA_LANGUAGE)
        if (request.year != null) {
            url.addQueryParameter(if (tv) "first_air_date_year" else "year", request.year.toString())
        }
        val body = get(key, url.build()) ?: return emptyList()
        return json.decodeFromString<TmdbPage>(body).results
    }

    /** Detaliile unui titlu (cu ID-ul IMDb, folosit mai târziu la subtitrări). null = nu există pe TMDB. */
    suspend fun details(
        key: String,
        type: MediaType,
        tmdbId: Long,
    ): TmdbItem? {
        val url =
            url(if (type == MediaType.TV) "tv/$tmdbId" else "movie/$tmdbId")
                .addQueryParameter("language", METADATA_LANGUAGE)
        if (type == MediaType.TV) url.addQueryParameter("append_to_response", "external_ids")
        val body = get(key, url.build()) ?: return null
        return json.decodeFromString<TmdbItem>(body)
    }

    private fun url(path: String): HttpUrl.Builder = "$baseUrl/$path".toHttpUrl().newBuilder()

    /** GET cu cheia; întoarce corpul, null la 404; aruncă [TmdbAuthException] la 401. */
    private suspend fun get(
        key: String,
        url: HttpUrl,
    ): String? {
        val bearer = key.startsWith("eyJ")
        val finalUrl = if (bearer) url else url.newBuilder().addQueryParameter("api_key", key).build()
        val request =
            Request
                .Builder()
                .url(finalUrl)
                .header("Accept", "application/json")
                .apply { if (bearer) header("Authorization", "Bearer $key") }
                .build()
        var attempt = 0
        while (true) {
            attempt++
            limiter.acquire()
            val result =
                try {
                    withContext(Dispatchers.IO) {
                        http.newCall(request).execute().use { response ->
                            when {
                                response.isSuccessful -> {
                                    Result.Body(response.body.string())
                                }

                                response.code == 404 -> {
                                    Result.Body(null)
                                }

                                response.code == 401 -> {
                                    throw TmdbAuthException()
                                }

                                response.code == 429 || response.code >= 500 -> {
                                    Result.Retry(response.header("Retry-After")?.toLongOrNull())
                                }

                                // Alte erori 4xx (ex. 422 la o căutare ciudată): tratăm ca „nimic găsit”.
                                else -> {
                                    Result.Body(null)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (attempt >= MAX_ATTEMPTS) throw e
                    Result.Retry(null)
                }
            when (result) {
                is Result.Body -> {
                    return result.text
                }

                is Result.Retry -> {
                    if (attempt >=
                        MAX_ATTEMPTS
                    ) {
                        throw IOException("TMDB nu răspunde (prea multe cereri sau eroare de server).")
                    }
                    val waitMs = (result.retryAfterSeconds?.times(1000)) ?: (1000L shl (attempt - 1))
                    limiter.pauseUntil(clock() + waitMs)
                    delay(waitMs)
                }
            }
        }
    }

    private sealed interface Result {
        data class Body(
            val text: String?,
        ) : Result

        data class Retry(
            val retryAfterSeconds: Long?,
        ) : Result
    }

    companion object {
        /** Limba metadatelor salvate (titlu, descriere). Căutarea poate folosi limba titlului din playlist. */
        const val METADATA_LANGUAGE = "en-US"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
        private const val MAX_ATTEMPTS = 5

        internal val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }
    }
}
