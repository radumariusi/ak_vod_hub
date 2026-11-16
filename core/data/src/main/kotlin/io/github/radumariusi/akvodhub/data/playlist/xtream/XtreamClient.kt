package io.github.radumariusi.akvodhub.data.playlist.xtream

import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamJson.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/**
 * Client pentru API-ul Xtream Codes (`player_api.php`). Folosim doar VOD:
 * `get_vod_streams`, `get_series`, `get_series_info` și categoriile lor. Live TV nu se cere niciodată.
 */
class XtreamClient(
    private val client: OkHttpClient,
) {
    /** Verifică contul. Aruncă [XtreamException] cu un mesaj pentru utilizator dacă nu e valid. */
    suspend fun authenticate(account: XtreamAccount) {
        val root = XtreamJson.parse(getText(account, null)) as? JsonObject
        val user = root?.get("user_info") as? JsonObject ?: throw XtreamException("Serverul nu pare a fi Xtream.")
        if (user.str("auth") != "1") throw XtreamException("Utilizator sau parolă greșite.")
        val status = user.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException("Contul nu e activ (stare: $status).")
        }
    }

    suspend fun vodCategories(account: XtreamAccount): Map<String, String> =
        XtreamJson.categories(XtreamJson.parse(getText(account, "get_vod_categories")))

    suspend fun seriesCategories(account: XtreamAccount): Map<String, String> =
        XtreamJson.categories(XtreamJson.parse(getText(account, "get_series_categories")))

    /** Filmele, citite în flux (pe firul de I/O). */
    suspend fun <T> movies(
        account: XtreamAccount,
        block: suspend (Sequence<XtreamMovie>) -> T,
    ): T =
        stream(
            account,
            "get_vod_streams",
        ) { input -> block(XtreamJson.objects(input).mapNotNull(XtreamJson::movie)) }

    /** Serialele (fără episoade), citite în flux. */
    suspend fun <T> series(
        account: XtreamAccount,
        block: suspend (Sequence<XtreamSeries>) -> T,
    ): T = stream(account, "get_series") { input -> block(XtreamJson.objects(input).mapNotNull(XtreamJson::series)) }

    /** Episoadele unui serial. Se cer la nevoie (un apel pe serial), nu la importul complet. */
    suspend fun episodes(
        account: XtreamAccount,
        seriesId: Long,
    ): List<XtreamEpisode> =
        XtreamJson.episodes(XtreamJson.parse(getText(account, "get_series_info", "series_id" to seriesId.toString())))

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

    private suspend fun getText(
        account: XtreamAccount,
        action: String?,
        vararg extra: Pair<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            execute(apiUrl(account, action, *extra)).use { it.body.string() }
        }

    private suspend fun <T> stream(
        account: XtreamAccount,
        action: String,
        block: suspend (InputStream) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            execute(apiUrl(account, action)).use { response -> response.body.byteStream().use { block(it) } }
        }

    private fun execute(url: HttpUrl): Response {
        val response =
            try {
                client.newCall(Request.Builder().url(url).build()).execute()
            } catch (e: IOException) {
                throw XtreamException("Serverul nu răspunde: ${e.message ?: e.javaClass.simpleName}")
            }
        if (!response.isSuccessful) {
            response.close()
            throw XtreamException(
                if (response.code == 401 || response.code == 403) {
                    "Acces refuzat de server (${response.code}). Verifică utilizatorul și parola."
                } else {
                    "Serverul a răspuns cu eroarea ${response.code}."
                },
            )
        }
        return response
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
