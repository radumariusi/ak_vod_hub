package io.github.radumariusi.akvodhub.data.update

import io.github.radumariusi.akvodhub.parser.update.UpdateApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed interface UpdateCheckResult {
    data class Available(
        val update: AppUpdate,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Failed(
        val message: String,
    ) : UpdateCheckResult
}

/**
 * Verifică ultimul release din GitHub și descarcă APK-ul.
 *
 * Folosește API-ul public, fără token. Dacă repo-ul devine privat, API-ul răspunde 404:
 * atunci release-urile trebuie publicate într-un repo public separat (`akvod.updateRepo`).
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val repo: String,
    private val supportedAbis: List<String>,
    private val json: Json = defaultJson,
) {
    suspend fun check(currentVersion: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("https://api.github.com/repos/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AK-VOD-Hub-Updater")
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> {
                            UpdateCheckResult.Failed("Nu există încă niciun release publicat.")
                        }

                        !response.isSuccessful -> {
                            UpdateCheckResult.Failed(
                                "GitHub a răspuns cu eroarea ${response.code}.",
                            )
                        }

                        else -> {
                            evaluate(response.body.string(), currentVersion)
                        }
                    }
                }
            } catch (e: IOException) {
                UpdateCheckResult.Failed("Fără conexiune la GitHub: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    internal fun evaluate(
        releaseJson: String,
        currentVersion: String,
    ): UpdateCheckResult {
        val update =
            parseRelease(releaseJson, supportedAbis, json)
                ?: return UpdateCheckResult.Failed("Release-ul nu conține un APK compatibil.")
        return if (AppVersion.isNewer(update.version, currentVersion)) {
            UpdateCheckResult.Available(update)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /** Descarcă APK-ul în [dest], raportând progresul (0..1). */
    suspend fun download(
        update: AppUpdate,
        dest: File,
        onProgress: (Float) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(update.apkUrl)
                    .header("User-Agent", "AK-VOD-Hub-Updater")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Descărcarea a eșuat (${response.code}).")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
                dest.parentFile?.mkdirs()
                val partial = File(dest.path + ".part")
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (!partial.renameTo(dest)) throw IOException("Nu pot salva APK-ul descărcat.")
                dest
            }
        }

    companion object {
        val defaultJson = Json { ignoreUnknownKeys = true }

        fun parseRelease(
            releaseJson: String,
            supportedAbis: List<String>,
            json: Json = defaultJson,
        ): AppUpdate? {
            val release = json.decodeFromString(GithubRelease.serializer(), releaseJson)
            if (release.draft || release.prerelease) return null
            val version = release.tagName.removePrefix("v")
            val assets = release.assets.associateBy { it.name }
            val name = UpdateApk.select(version, supportedAbis, assets.keys) ?: return null
            val apk = assets.getValue(name)
            return AppUpdate(
                version = version,
                notes = release.body.orEmpty().trim(),
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
                pageUrl = release.htmlUrl,
            )
        }
    }
}
