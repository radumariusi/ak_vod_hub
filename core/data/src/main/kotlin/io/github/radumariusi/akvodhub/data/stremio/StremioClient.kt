package io.github.radumariusi.akvodhub.data.stremio

import io.github.radumariusi.akvodhub.parser.stremio.AddonSubtitle
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Eroare citibilă pentru utilizator (addon inexistent, nu dă subtitrări etc.). */
class StremioException(
    message: String,
) : IOException(message)

/** Client pentru protocolul addon-urilor Stremio: manifestul, lista de subtitrări și descărcarea lor. */
class StremioClient(
    client: OkHttpClient,
) {
    /** Addon-urile lente nu țin playerul în așteptare: o cerere durează cel mult 25 s. */
    private val http = client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()

    suspend fun manifest(manifestUrl: String): StremioManifest {
        val body = getText(manifestUrl, MAX_JSON_BYTES)
        return StremioJson.manifest(body) ?: throw StremioException("Adresa nu e a unui addon Stremio.")
    }

    suspend fun subtitles(
        addon: StremioAddon,
        type: String,
        videoId: String,
    ): List<AddonSubtitle> {
        val url = StremioIds.subtitlesUrl(StremioIds.baseUrl(addon.manifestUrl), type, videoId)
        return StremioJson.subtitles(getText(url, MAX_JSON_BYTES), addon.name)
    }

    /** Fișierul subtitrării (cel mult [MAX_SUBTITLE_BYTES]). */
    suspend fun download(url: String): ByteArray = get(url, MAX_SUBTITLE_BYTES)

    private suspend fun getText(
        url: String,
        limit: Long,
    ): String = String(get(url, limit), Charsets.UTF_8)

    private suspend fun get(
        url: String,
        limit: Long,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val request =
                try {
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                } catch (e: IllegalArgumentException) {
                    throw StremioException("Adresă invalidă.")
                }
            http.newCall(request).execute().use { response ->
                if (response.code == 404) throw StremioException("Addon-ul a răspuns 404 (adresă greșită?).")
                if (!response.isSuccessful) throw StremioException("Addon-ul a răspuns cu eroarea ${response.code}.")
                val body = response.body
                val length = body.contentLength()
                if (length > limit) throw StremioException("Răspuns prea mare.")
                val source = body.source()
                if (!source.request(limit + 1)) {
                    source.buffer.readByteArray()
                } else {
                    throw StremioException("Răspuns prea mare.")
                }
            }
        }

    private companion object {
        const val USER_AGENT = "AKVodHub (Android TV)"
        const val MAX_JSON_BYTES = 4L * 1024 * 1024
        const val MAX_SUBTITLE_BYTES = 6L * 1024 * 1024
    }
}
