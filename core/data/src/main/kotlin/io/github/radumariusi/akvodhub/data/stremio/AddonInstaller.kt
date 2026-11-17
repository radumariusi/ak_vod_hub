package io.github.radumariusi.akvodhub.data.stremio

import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import kotlinx.coroutines.CancellationException

/**
 * Adăugarea unui addon după adresă (din Setări sau de pe telefon): verifică manifestul și păstrează addon-ul
 * doar dacă oferă subtitrări sau cataloage. Aruncă [IllegalArgumentException] cu mesajul pentru utilizator.
 */
class AddonInstaller(
    private val client: StremioClient,
    private val addons: StremioAddons,
) {
    suspend fun install(input: String): StremioAddon {
        val url =
            StremioIds.normalizeManifestUrl(input)
                ?: throw IllegalArgumentException("Adresa nu pare corectă. Exemplu: https://opensubtitles-v3.strem.io")
        val manifest =
            try {
                client.manifest(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Addon-ul nu se poate citi: ${SubtitleRepository.reason(e)}.")
            }
        val resources = manifest.resources.map { it.name }.distinct()
        require("subtitles" in resources || "catalog" in resources) {
            "„${manifest.name}” nu oferă subtitrări sau cataloage."
        }
        val addon = StremioAddon(url, manifest.name, resources)
        addons.add(addon)
        return addon
    }
}
