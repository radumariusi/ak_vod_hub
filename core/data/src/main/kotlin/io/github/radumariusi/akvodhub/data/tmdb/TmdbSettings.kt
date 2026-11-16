package io.github.radumariusi.akvodhub.data.tmdb

import android.content.Context
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TmdbState(
    val hasKey: Boolean,
    /** Ultima eroare a potrivirii (ex. cheie respinsă, fără rețea), null dacă a mers. */
    val lastError: String?,
    val lastRunAt: Long?,
)

/** Cheia TMDB a utilizatorului (criptată, doar pe dispozitiv) și starea ultimei potriviri. */
class TmdbSettings(
    context: Context,
    private val cipher: SecretCipher,
) {
    private val prefs = context.getSharedPreferences("tmdb", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<TmdbState> = mutableState.asStateFlow()

    fun apiKey(): String? = prefs.getString(KEY, null)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    fun saveKey(key: String) {
        prefs
            .edit()
            .putString(KEY, cipher.encrypt(key))
            .remove(ERROR)
            .apply()
        mutableState.value = read()
    }

    fun clearKey() {
        prefs.edit().remove(KEY).apply()
        mutableState.value = read()
    }

    fun recordRun(
        error: String?,
        at: Long,
    ) {
        prefs
            .edit()
            .putString(ERROR, error)
            .putLong(LAST_RUN, at)
            .apply()
        mutableState.value = read()
    }

    private fun read() =
        TmdbState(
            hasKey = prefs.contains(KEY),
            lastError = prefs.getString(ERROR, null),
            lastRunAt = prefs.getLong(LAST_RUN, 0L).takeIf { it > 0 },
        )

    private companion object {
        const val KEY = "api_key"
        const val ERROR = "last_error"
        const val LAST_RUN = "last_run"
    }
}
