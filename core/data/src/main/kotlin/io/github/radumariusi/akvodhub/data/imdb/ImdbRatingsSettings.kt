package io.github.radumariusi.akvodhub.data.imdb

import android.content.Context
import io.github.radumariusi.akvodhub.parser.catalog.ImdbRatings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImdbRatingsState(
    val lastUpdated: Long? = null,
    val updatedTitles: Int = 0,
    val lastError: String? = null,
    val running: Boolean = false,
)

class ImdbRatingsSettings(context: Context) {
    private val prefs = context.getSharedPreferences("imdb_ratings", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()

    fun isDue(now: Long = System.currentTimeMillis()) = ImdbRatings.isDue(state.value.lastUpdated, now)

    fun success(at: Long, titles: Int) {
        prefs
            .edit()
            .putLong("updated", at)
            .putInt("titles", titles)
            .remove("error")
            .apply()
        mutableState.value = read()
    }

    fun failure(message: String) {
        prefs.edit().putString("error", message).apply()
        mutableState.value = read()
    }

    private fun read() =
        ImdbRatingsState(
            lastUpdated = prefs.getLong("updated", 0L).takeIf { it > 0 },
            updatedTitles = prefs.getInt("titles", 0),
            lastError = prefs.getString("error", null),
        )
}
