package io.github.radumariusi.akvodhub.tv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri

/** Ce cere un intent venit din afara aplicației (ecranul principal Android TV, căutarea globală, Asistentul). */
sealed interface NavRequest {
    data class OpenTitle(
        val titleId: Long,
    ) : NavRequest

    /** Pornește direct redarea, de unde a rămas (din rândul „Watch Next”). */
    data class Play(
        val titleId: Long,
    ) : NavRequest

    data class Search(
        val text: String,
    ) : NavRequest
}

/**
 * Linkurile aplicației: `akvodhub://title/<id>` (fișa titlului) și `akvodhub://play/<id>` (continuă redarea).
 * ID-ul e cel din baza locală; după o reinstalare, linkurile vechi de pe ecranul principal nu mai duc nicăieri.
 */
object DeepLinks {
    const val SCHEME = "akvodhub"
    private const val ASSISTANT_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"

    fun title(titleId: Long): Uri = Uri.parse("$SCHEME://title/$titleId")

    fun play(titleId: Long): Uri = Uri.parse("$SCHEME://play/$titleId")

    fun parse(intent: Intent?): NavRequest? {
        intent ?: return null
        when (intent.action) {
            Intent.ACTION_SEARCH, ASSISTANT_SEARCH -> {
                val text = intent.getStringExtra(SearchManager.QUERY)?.trim()
                return text?.takeIf { it.isNotEmpty() }?.let { NavRequest.Search(it) }
            }

            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return null
                if (uri.scheme != SCHEME) return null
                val id = uri.lastPathSegment?.toLongOrNull() ?: return null
                return when (uri.host) {
                    "title" -> NavRequest.OpenTitle(id)
                    "play" -> NavRequest.Play(id)
                    else -> null
                }
            }
        }
        return null
    }
}
