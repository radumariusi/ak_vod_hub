package io.github.radumariusi.akvodhub.data.stremio

import android.content.Context
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un addon instalat: adresa manifestului și numele lui (din manifest). */
@Serializable
data class StremioAddon(
    val manifestUrl: String,
    val name: String,
    /** Resursele din manifest (ex. `subtitles`, `catalog`), pentru etapa cu cataloage. */
    val resources: List<String> = emptyList(),
)

/**
 * Addon-urile Stremio folosite de aplicație (doar pe dispozitiv). La prima pornire: OpenSubtitles v3.
 * Adresele pot conține configurări personale, deci rămân în preferințele private ale aplicației.
 */
class StremioAddons(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("stremio", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(read())
    val addons: StateFlow<List<StremioAddon>> = mutable.asStateFlow()

    fun add(addon: StremioAddon) {
        save(mutable.value.filter { it.manifestUrl != addon.manifestUrl } + addon)
    }

    fun remove(manifestUrl: String) {
        save(mutable.value.filter { it.manifestUrl != manifestUrl })
    }

    /** Mută addon-ul cu o poziție mai sus (ordinea contează în lista de subtitrări). */
    fun moveUp(manifestUrl: String) {
        val list = mutable.value.toMutableList()
        val i = list.indexOfFirst { it.manifestUrl == manifestUrl }
        if (i <= 0) return
        list.add(i - 1, list.removeAt(i))
        save(list)
    }

    private fun save(list: List<StremioAddon>) {
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
        mutable.value = list
    }

    private fun read(): List<StremioAddon> {
        val saved = prefs.getString(KEY, null) ?: return listOf(OPENSUBTITLES)
        return runCatching { json.decodeFromString<List<StremioAddon>>(saved) }.getOrDefault(listOf(OPENSUBTITLES))
    }

    companion object {
        private const val KEY = "addons"

        val OPENSUBTITLES =
            StremioAddon(StremioIds.OPENSUBTITLES_MANIFEST, "OpenSubtitles v3", listOf("subtitles"))
    }
}
