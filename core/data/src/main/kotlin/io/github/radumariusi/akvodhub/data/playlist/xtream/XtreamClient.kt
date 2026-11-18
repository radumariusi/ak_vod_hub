package io.github.radumariusi.akvodhub.data.playlist.xtream

import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamJson.str
import io.github.radumariusi.akvodhub.parser.playlist.JsonObjectStreamException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Client pentru API-ul Xtream Codes (`player_api.php`). Folosim doar VOD:
 * `get_vod_streams`, `get_series`, `get_series_info` și categoriile lor. Live TV nu se cere niciodată.
 */
class XtreamClient(
    private val client: OkHttpClient,
) {
    /** Verifică contul. Aruncă [XtreamException] cu un mesaj pentru utilizator dacă nu e valid. */
    suspend fun authenticate(account: XtreamAccount) =
        jsonOperation("Autentificare") {
            val root = XtreamJson.parse(getText(account, null)) as? JsonObject
            val user = root?.get("user_info") as? JsonObject ?: throw XtreamException("structură neașteptată")
            if (user.str("auth") != "1") throw XtreamException("utilizator sau parolă greșite")
            val status = user.str("status")
            if (status != null && !status.equals("Active", ignoreCase = true)) {
                throw XtreamException("contul nu este activ")
            }
        }

    suspend fun vodCategories(account: XtreamAccount): Map<String, String> = categories(account, "get_vod_categories")

    suspend fun seriesCategories(account: XtreamAccount): Map<String, String> =
        categories(
            account,
            "get_series_categories",
        )

    private suspend fun categories(account: XtreamAccount, action: String): Map<String, String> =
        jsonOperation(action) {
            val root = XtreamJson.parse(getText(account, action))
            if (root !is JsonArray) throw XtreamException("listă de categorii așteptată")
            XtreamJson.categories(root)
        }

    /** Descarcă în cache înaintea tranzacției de publicare, fără a ține răspunsul complet în memorie. */
    suspend fun downloadCatalog(account: XtreamAccount, action: String, file: File) {
        jsonOperation(action) {
            request(account, action) { input ->
                file.outputStream().buffered().use { output ->
                    val context = currentCoroutineContext()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    suspend fun <T> movies(file: File, block: suspend (Sequence<XtreamMovie>) -> T): T =
        readCatalog(file, "get_vod_streams", XtreamJson::movie, block)

    suspend fun <T> series(file: File, block: suspend (Sequence<XtreamSeries>) -> T): T =
        readCatalog(file, "get_series", XtreamJson::series, block)

    private suspend fun <E, T> readCatalog(
        file: File,
        action: String,
        parse: (JsonObject) -> E?,
        block: suspend (Sequence<E>) -> T,
    ): T =
        jsonOperation(action) {
            val context = currentCoroutineContext()
            file.inputStream().use { input ->
                block(
                    XtreamJson.objects(input).mapIndexed { index, row ->
                        context.ensureActive()
                        parse(row) ?: throw XtreamException("identificator absent sau invalid la intrarea ${index + 1}")
                    },
                )
            }
        }

    /** Episoadele unui serial. Se cer la nevoie (un apel pe serial), nu la importul complet. */
    suspend fun episodes(account: XtreamAccount, seriesId: Long): List<XtreamEpisode> =
        jsonOperation("get_series_info") {
            XtreamJson.episodes(
                XtreamJson.parse(getText(account, "get_series_info", "series_id" to seriesId.toString())),
            )
        }

    private fun apiUrl(
        account: XtreamAccount,
        action: String?,
        vararg extra: Pair<String, String>,
    ): HttpUrl {
        val base =
            "${account.server}/player_api.php".toHttpUrlOrNull()
                ?: throw XtreamException("Adresa serverului nu e validă.")
        return base
            .newBuilder()
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                extra.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
    }

    private suspend fun getText(account: XtreamAccount, action: String?, vararg extra: Pair<String, String>): String =
        request(account, action, *extra) { input -> input.bufferedReader(Charsets.UTF_8).readText() }

    private suspend fun <T> request(
        account: XtreamAccount,
        action: String?,
        vararg extra: Pair<String, String>,
        read: suspend (InputStream) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val call = client.newCall(Request.Builder().url(apiUrl(account, action, *extra)).build())
                if (action == "get_series_info") call.timeout().timeout(30, TimeUnit.SECONDS)
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) { cancelCallOnExit(call) }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw XtreamException("HTTP ${response.code}")
                        response.body.byteStream().use { read(it) }
                    }
                } finally {
                    cancellation.cancel()
                }
            }
        }

    private suspend fun cancelCallOnExit(call: Call) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }

    private suspend fun <T> jsonOperation(action: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: XtreamException) {
            throw XtreamException("$action: ${e.message}")
        } catch (e: JsonObjectStreamException) {
            throw XtreamException("$action: JSON incomplet sau invalid (${e.reason})")
        } catch (_: SerializationException) {
            throw XtreamException("$action: structură JSON invalidă")
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw XtreamException("$action: eroare I/O (${e.javaClass.simpleName})")
        }
    }

    companion object {
        /** Linkul de redare (construit la cerere, ca parola să nu fie salvată în fiecare intrare). */
        fun streamUrl(
            account: XtreamAccount,
            kind: String,
            id: Long,
            extension: String?,
        ): String {
            val ext = extension?.trim()?.trimStart('.')?.ifEmpty { null } ?: "mp4"
            return "${account.server}/$kind/${account.username}/${account.password}/$id.$ext"
        }
    }
}
