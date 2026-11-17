package io.github.radumariusi.akvodhub.data.remote

import io.github.radumariusi.akvodhub.data.backup.SettingsBackup
import io.github.radumariusi.akvodhub.data.catalog.CatalogRepository
import io.github.radumariusi.akvodhub.data.playlist.PlaylistRepository
import io.github.radumariusi.akvodhub.data.stremio.AddonInstaller
import io.github.radumariusi.akvodhub.parser.remote.HttpMessage
import io.github.radumariusi.akvodhub.parser.remote.HttpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Adresa paginii de configurare (codul QR de pe TV duce aici). */
data class SetupAddress(
    val url: String,
    val host: String,
    val port: Int,
)

/**
 * Configurare de pe telefon: un server HTTP mic, pornit doar cât timp ecranul „Configurare de pe telefon” e
 * deschis pe TV. Telefonul (în aceeași rețea) scanează codul QR și primește o pagină unde lipește linkul M3U,
 * contul Xtream, cheia TMDB sau un addon Stremio, și de unde descarcă / încarcă backup-ul setărilor.
 *
 * Fiecare pornire are un cod secret nou în adresă (`?t=…`); fără el, serverul refuză orice cerere, deci un alt
 * dispozitiv din rețea nu poate schimba setările doar știind portul.
 */
class PhoneSetupServer(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val installer: AddonInstaller,
    private val backup: SettingsBackup,
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null
    private var token = ""
    private val mutableEvents = MutableStateFlow<List<String>>(emptyList())

    /** Ce s-a făcut de pe telefon în sesiunea curentă (cele mai noi primele). */
    val events: StateFlow<List<String>> = mutableEvents.asStateFlow()

    /** Pornește serverul (dacă nu rulează deja). Null = TV-ul nu e în nicio rețea locală. */
    @Synchronized
    fun start(scope: CoroutineScope): SetupAddress? {
        val host = localAddress() ?: return null
        val server =
            socket ?: runCatching { ServerSocket(PORT) }.getOrElse { ServerSocket(0) }.also { s ->
                socket = s
                token = newToken()
                mutableEvents.value = emptyList()
                job = scope.launch(Dispatchers.IO) { acceptLoop(s, this) }
            }
        return SetupAddress("http://$host:${server.localPort}/?t=$token", host, server.localPort)
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private fun acceptLoop(
        server: ServerSocket,
        scope: CoroutineScope,
    ) {
        while (scope.isActive && !server.isClosed) {
            val client =
                try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
            scope.launch(Dispatchers.IO) { client.use { handle(it) } }
        }
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MS
        val response =
            try {
                route(HttpMessage.read(client.getInputStream()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                HttpMessage.response(400, TEXT, "Cerere invalidă.".toByteArray())
            } catch (_: Exception) {
                HttpMessage.response(500, TEXT, "Eroare pe TV.".toByteArray())
            }
        runCatching {
            client.getOutputStream().apply {
                write(response)
                flush()
            }
        }
    }

    private suspend fun route(request: HttpRequest): ByteArray {
        if (request.query["t"] != token) {
            return HttpMessage.response(403, HTML, SetupPage.EXPIRED.toByteArray())
        }
        return when (request.method to request.path) {
            "GET" to "/" -> {
                HttpMessage.response(200, HTML, SetupPage.HTML.toByteArray())
            }

            "GET" to "/api/backup" -> {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                val body = backup.export().toByteArray()
                event("Backup descărcat pe telefon")
                HttpMessage.response(
                    200,
                    "application/json; charset=utf-8",
                    body,
                    mapOf("Content-Disposition" to "attachment; filename=\"ak-vod-hub-backup-$day.json\""),
                )
            }

            "POST" to "/api/m3u",
            "POST" to "/api/xtream",
            "POST" to "/api/tmdb",
            "POST" to "/api/addon",
            "POST" to "/api/restore",
            -> {
                action(request)
            }

            else -> {
                HttpMessage.response(404, TEXT, "Nu există.".toByteArray())
            }
        }
    }

    private suspend fun action(request: HttpRequest): ByteArray {
        val message =
            try {
                Result.success(perform(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        val ok = message.isSuccess
        val text = message.getOrElse { it.message ?: "Nu a mers." }
        if (ok) event(text)
        val json = JsonObject(mapOf("ok" to JsonPrimitive(ok), "message" to JsonPrimitive(text)))
        return HttpMessage.response(200, "application/json; charset=utf-8", json.toString().toByteArray())
    }

    private suspend fun perform(request: HttpRequest): String {
        if (request.path == "/api/restore") {
            return "Backup restaurat: " + backup.restore(request.body).summary()
        }
        val form =
            runCatching { Json.parseToJsonElement(request.body).jsonObject }
                .getOrElse { throw IllegalArgumentException("Cerere invalidă.") }

        fun field(name: String) =
            form[name]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                .orEmpty()
        return when (request.path) {
            "/api/m3u" -> {
                require(field("url").isNotEmpty()) { "Lipsește linkul M3U." }
                playlists.addM3uUrl(field("name"), field("url"))
                "Playlist M3U adăugat. Importul a pornit pe TV."
            }

            "/api/xtream" -> {
                require(field("server").isNotEmpty() && field("username").isNotEmpty()) {
                    "Completează serverul, utilizatorul și parola."
                }
                playlists.addXtream(field("name"), field("server"), field("username"), field("password"))
                "Cont Xtream adăugat. Importul a pornit pe TV."
            }

            "/api/tmdb" -> {
                catalog.saveTmdbKey(field("key"))
                "Cheia TMDB e salvată. Potrivirea titlurilor a pornit."
            }

            else -> {
                "Addon adăugat: " + installer.install(field("url")).name + "."
            }
        }
    }

    private fun event(text: String) {
        mutableEvents.value = (listOf(text) + mutableEvents.value).take(MAX_EVENTS)
    }

    private fun newToken(): String {
        val random = SecureRandom()
        return (1..TOKEN_LENGTH).map { TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)] }.joinToString("")
    }

    companion object {
        const val PORT = 8686
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val TOKEN_LENGTH = 10
        private const val TOKEN_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"
        private const val MAX_EVENTS = 5
        private const val HTML = "text/html; charset=utf-8"
        private const val TEXT = "text/plain; charset=utf-8"

        /** Adresa IPv4 a TV-ului în rețeaua locală (Wi-Fi sau cablu); preferăm adresele private (192.168…). */
        fun localAddress(): String? =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .sortedByDescending { it.isSiteLocalAddress }
                    .firstOrNull()
                    ?.hostAddress
            }.getOrNull()
    }
}
