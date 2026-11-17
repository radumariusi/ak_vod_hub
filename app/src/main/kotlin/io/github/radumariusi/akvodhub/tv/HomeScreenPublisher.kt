package io.github.radumariusi.akvodhub.tv

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.radumariusi.akvodhub.R
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.db.ContinueItem
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.player.Resume
import io.github.radumariusi.akvodhub.parser.tv.HomeScreenSync
import io.github.radumariusi.akvodhub.parser.tv.PublishedRow
import io.github.radumariusi.akvodhub.ui.hub.Images
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ecranul principal Android TV (Android 8+):
 * - rândul sistemului **„Watch Next” / „Continuă vizionarea”** primește filmele și serialele începute (și
 *   episodul următor), cu poziția; alegerea pornește redarea de unde ai rămas;
 * - un canal al aplicației, **„AK VOD Hub: adăugate recent”**, cu ultimele titluri apărute în playlist-uri.
 *
 * Se actualizează după fiecare salvare a progresului și după fiecare import / potrivire, ca diferență (nu
 * ștergem și rescriem tot). Pe dispozitivele fără ecran principal TV (telefoane) nu face nimic.
 */
@Singleton
class HomeScreenPublisher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val repository: HubRepository,
    ) {
        private val supported =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        fun start(scope: CoroutineScope) {
            if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            launchSync(scope)
        }

        @OptIn(FlowPreview::class)
        @RequiresApi(Build.VERSION_CODES.O)
        private fun launchSync(scope: CoroutineScope) {
            scope.launch(Dispatchers.IO) {
                safely("start") {
                    syncWatchNext()
                    syncChannel()
                }
                launch {
                    merge(repository.watchVersion.drop(1), repository.catalogVersion.drop(1))
                        .debounce(DEBOUNCE_MS)
                        .collect { safely("watch next") { syncWatchNext() } }
                }
                launch {
                    repository.catalogVersion
                        .drop(1)
                        .debounce(DEBOUNCE_MS)
                        .collect { safely("canal") { syncChannel() } }
                }
            }
        }

        private inline fun safely(
            what: String,
            block: () -> Unit,
        ) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unele box-uri n-au furnizorul TV sau îl blochează: aplicația merge oricum.
                Log.w(TAG, "Ecranul principal ($what): ${e.message}")
            }
        }

        // ------------------------------------------------------------ Watch Next

        @RequiresApi(Build.VERSION_CODES.O)
        private suspend fun syncWatchNext() {
            val items = repository.continueItems(MAX_WATCH_NEXT)
            val resolver = context.contentResolver
            val published = published(resolver, TvContract.WatchNextPrograms.CONTENT_URI)
            val byKey = items.associateBy { WATCH_PREFIX + it.titleId }
            val plan = HomeScreenSync.plan(items.map { WATCH_PREFIX + it.titleId }, published)
            plan.delete.forEach { resolver.delete(TvContract.buildWatchNextProgramUri(it), null, null) }
            plan.update.forEach { (key, rowId) ->
                val item = byKey[key] ?: return@forEach
                resolver.update(TvContract.buildWatchNextProgramUri(rowId), watchNext(item), null, null)
            }
            plan.insert.forEach { key ->
                byKey[key]?.let { resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, watchNext(it)) }
            }
        }

        private fun watchNext(item: ContinueItem): ContentValues =
            ContentValues().apply {
                val series = item.mediaType == MediaType.TV
                val started = item.positionMs >= Resume.MIN_POSITION_MS
                put(Col.INTERNAL_PROVIDER_ID, WATCH_PREFIX + item.titleId)
                put(Col.TYPE, if (series) TYPE_TV_EPISODE else TYPE_MOVIE)
                put(Col.WATCH_NEXT_TYPE, if (started) WATCH_NEXT_CONTINUE else WATCH_NEXT_NEXT)
                put(Col.TITLE, item.name)
                if (series && item.season != null && item.episode != null) {
                    put(Col.SEASON_NUMBER, item.season.toString())
                    put(Col.EPISODE_NUMBER, item.episode.toString())
                    put(Col.EPISODE_TITLE, "S%02dE%02d".format(item.season, item.episode))
                }
                item.overview?.let { put(Col.DESCRIPTION, it.take(MAX_DESCRIPTION)) }
                common(item.posterPath, item.year)
                if (item.durationMs > 0) put(Col.DURATION, item.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                if (started) put(Col.POSITION, item.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                put(Col.LAST_ENGAGEMENT, item.updatedAt)
                put(Col.INTENT_URI, intentUri(DeepLinks.play(item.titleId)))
            }

        // ------------------------------------------------------------ canalul „adăugate recent”

        @RequiresApi(Build.VERSION_CODES.O)
        private suspend fun syncChannel() {
            val cards = repository.page(CatalogFilter(sort = CatalogSort.RECENT), MAX_CHANNEL).items
            val resolver = context.contentResolver
            val channelId = channel(resolver, create = cards.isNotEmpty()) ?: return
            val programsUri = TvContract.buildPreviewProgramsUriForChannel(channelId)
            val published = published(resolver, programsUri)
            val byKey = cards.associateBy { RECENT_PREFIX + it.id }
            val plan = HomeScreenSync.plan(cards.map { RECENT_PREFIX + it.id }, published)
            val weights = cards.withIndex().associate { (i, c) -> RECENT_PREFIX + c.id to cards.size - i }
            plan.delete.forEach { resolver.delete(TvContract.buildPreviewProgramUri(it), null, null) }
            plan.update.forEach { (key, rowId) ->
                val card = byKey[key] ?: return@forEach
                resolver.update(TvContract.buildPreviewProgramUri(rowId), preview(card, channelId, weights), null, null)
            }
            plan.insert.forEach { key ->
                val card = byKey[key] ?: return@forEach
                resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, preview(card, channelId, weights))
            }
        }

        private fun preview(
            card: TitleCard,
            channelId: Long,
            weights: Map<String, Int>,
        ): ContentValues =
            ContentValues().apply {
                val key = RECENT_PREFIX + card.id
                put(Col.CHANNEL_ID, channelId)
                put(Col.INTERNAL_PROVIDER_ID, key)
                put(Col.TYPE, if (card.mediaType == MediaType.TV) TYPE_TV_SERIES else TYPE_MOVIE)
                put(Col.TITLE, card.name)
                card.overview?.let { put(Col.DESCRIPTION, it.take(MAX_DESCRIPTION)) }
                common(card.posterPath, card.year)
                put(Col.WEIGHT, weights[key] ?: 0)
                put(Col.INTENT_URI, intentUri(DeepLinks.title(card.id)))
            }

        /** Canalul nostru (îl creează la prima rulare, cu logo, și cere să apară pe ecranul principal). */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun channel(
            resolver: ContentResolver,
            create: Boolean,
        ): Long? {
            resolver
                .query(TvContract.Channels.CONTENT_URI, arrayOf(Col.ID, Col.INTERNAL_PROVIDER_ID), null, null, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) == CHANNEL_KEY) return c.getLong(0)
                    }
                }
            if (!create) return null
            val values =
                ContentValues().apply {
                    put(Col.TYPE, TvContract.Channels.TYPE_PREVIEW)
                    put(Col.DISPLAY_NAME, context.getString(R.string.channel_name))
                    put(Col.INTERNAL_PROVIDER_ID, CHANNEL_KEY)
                    put(Col.APP_LINK_INTENT_URI, launchIntentUri())
                }
            val uri = resolver.insert(TvContract.Channels.CONTENT_URI, values) ?: return null
            val id = uri.lastPathSegment?.toLongOrNull() ?: return null
            runCatching {
                val logo = ContextCompat.getDrawable(context, R.drawable.ic_launcher)?.toBitmap(LOGO_PX, LOGO_PX)
                if (logo != null) {
                    resolver.openOutputStream(TvContract.buildChannelLogoUri(id))?.use {
                        logo.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            // Primul canal al unei aplicații poate apărea direct; altfel se adaugă din „Personalizează canalele”.
            runCatching { TvContract.requestChannelBrowsable(context, id) }
            return id
        }

        private fun ContentValues.common(
            posterPath: String?,
            year: Int?,
        ) {
            Images.poster(posterPath)?.let {
                put(Col.POSTER_URI, it)
                put(Col.POSTER_ASPECT, ASPECT_RATIO_2_3)
            }
            year?.let { put(Col.RELEASE_DATE, it.toString()) }
        }

        private fun published(
            resolver: ContentResolver,
            uri: Uri,
        ): List<PublishedRow> =
            resolver.query(uri, arrayOf(Col.ID, Col.INTERNAL_PROVIDER_ID, Col.BROWSABLE), null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val key = c.getString(1) ?: continue
                        add(PublishedRow(key, c.getLong(0), c.getInt(2) != 0))
                    }
                }
            } ?: emptyList()

        private fun intentUri(uri: Uri): String =
            Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName).toUri(Intent.URI_INTENT_SCHEME)

        private fun launchIntentUri(): String =
            (context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName) ?: Intent())
                .toUri(Intent.URI_INTENT_SCHEME)

        /** Numele coloanelor din `TvContract` (scrise aici ca să nu depindem de interfețele ascunse ale SDK-ului). */
        private object Col {
            const val ID = "_id"
            const val INTERNAL_PROVIDER_ID = "internal_provider_id"
            const val TYPE = "type"
            const val WATCH_NEXT_TYPE = "watch_next_type"
            const val TITLE = "title"
            const val EPISODE_TITLE = "episode_title"
            const val SEASON_NUMBER = "season_display_number"
            const val EPISODE_NUMBER = "episode_display_number"
            const val DESCRIPTION = "short_description"
            const val POSTER_URI = "poster_art_uri"
            const val POSTER_ASPECT = "poster_art_aspect_ratio"
            const val DURATION = "duration_millis"
            const val POSITION = "last_playback_position_millis"
            const val LAST_ENGAGEMENT = "last_engagement_time_utc_millis"
            const val INTENT_URI = "intent_uri"
            const val BROWSABLE = "browsable"
            const val CHANNEL_ID = "channel_id"
            const val RELEASE_DATE = "release_date"
            const val WEIGHT = "weight"
            const val DISPLAY_NAME = "display_name"
            const val APP_LINK_INTENT_URI = "app_link_intent_uri"
        }

        private companion object {
            const val TAG = "HomeScreen"
            const val WATCH_PREFIX = "w:"
            const val RECENT_PREFIX = "r:"
            const val CHANNEL_KEY = "recent"
            const val MAX_WATCH_NEXT = 12
            const val MAX_CHANNEL = 24
            const val MAX_DESCRIPTION = 300
            const val LOGO_PX = 256
            const val DEBOUNCE_MS = 2_000L

            // Valorile din TvContract (PreviewPrograms / WatchNextPrograms).
            const val TYPE_MOVIE = 0
            const val TYPE_TV_SERIES = 1
            const val TYPE_TV_EPISODE = 3
            const val WATCH_NEXT_CONTINUE = 0
            const val WATCH_NEXT_NEXT = 1
            const val ASPECT_RATIO_2_3 = 4
        }
    }
