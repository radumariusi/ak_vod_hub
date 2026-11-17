package io.github.radumariusi.akvodhub.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.ui.hub.Images
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Căutarea globală Android TV (inclusiv cea vocală, de pe butonul de microfon al telecomenzii): sistemul cere
 * sugestii de la aplicațiile „căutabile”, iar noi răspundem din indexul local, deci doar cu ce e în playlist.
 * Un rezultat ales deschide fișa titlului (`akvodhub://title/<id>`).
 *
 * Pe unele TV-uri trebuie bifată aplicația în Setări → Preferințe dispozitiv → Căutare → Aplicații căutabile.
 */
class SearchProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun hubRepository(): HubRepository
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val text =
            (selectionArgs?.firstOrNull() ?: uri.lastPathSegment)
                ?.takeUnless { it == SearchManager.SUGGEST_URI_PATH_QUERY }
                ?.trim()
                .orEmpty()
        if (text.length < 2) return cursor
        val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val context = context ?: return cursor
        val repository = EntryPointAccessors.fromApplication(context, Deps::class.java).hubRepository()
        val cards =
            runCatching {
                runBlocking { withTimeoutOrNull(TIMEOUT_MS) { repository.page(CatalogFilter(text = text), limit, 0) } }
            }.getOrNull().orEmpty()
        for (card in cards) {
            cursor.addRow(
                arrayOf<Any?>(
                    card.id,
                    card.name,
                    listOfNotNull(card.year?.toString(), if (card.mediaType == MediaType.TV) "Serial" else "Film")
                        .joinToString(" · "),
                    Images.poster(card.posterPath),
                    if (card.mediaType == MediaType.TV) "video/x-tv-series" else "video/mp4",
                    0,
                    card.year,
                    card.id.toString(),
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val TIMEOUT_MS = 4_000L

        val COLUMNS =
            arrayOf(
                BaseColumns._ID,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2,
                SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
                SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
                SearchManager.SUGGEST_COLUMN_IS_LIVE,
                SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
            )
    }
}
