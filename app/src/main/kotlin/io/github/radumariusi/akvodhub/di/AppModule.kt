package io.github.radumariusi.akvodhub.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.radumariusi.akvodhub.BuildConfig
import io.github.radumariusi.akvodhub.data.backup.SettingsBackup
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.catalog.CatalogRepository
import io.github.radumariusi.akvodhub.data.catalog.HubRepository
import io.github.radumariusi.akvodhub.data.catalog.MatchScheduler
import io.github.radumariusi.akvodhub.data.catalog.TitleMatcher
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.discover.DiscoverRepository
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsScheduler
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsSettings
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsUpdater
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.playlist.PlaylistRepository
import io.github.radumariusi.akvodhub.data.playlist.PlaylistScheduler
import io.github.radumariusi.akvodhub.data.playlist.PlaylistWorkerFactory
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.remote.PhoneSetupServer
import io.github.radumariusi.akvodhub.data.security.KeystoreSecretCipher
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import io.github.radumariusi.akvodhub.data.stremio.AddonInstaller
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import io.github.radumariusi.akvodhub.data.stremio.StremioClient
import io.github.radumariusi.akvodhub.data.stremio.SubtitleRepository
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.data.update.UpdateChecker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun updateChecker(client: OkHttpClient): UpdateChecker = UpdateChecker(client, BuildConfig.UPDATE_REPO)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): AkVodDatabase = AkVodDatabase.create(context)

    @Provides
    @Singleton
    fun secretCipher(): SecretCipher = KeystoreSecretCipher()

    @Provides
    @Singleton
    fun xtreamClient(client: OkHttpClient): XtreamClient = XtreamClient(client)

    @Provides
    @Singleton
    fun playlistImporter(
        @ApplicationContext context: Context,
        db: AkVodDatabase,
        cipher: SecretCipher,
        client: OkHttpClient,
        xtream: XtreamClient,
    ): PlaylistImporter = PlaylistImporter(context, db, cipher, client, xtream)

    @Provides
    @Singleton
    fun playlistScheduler(
        @ApplicationContext context: Context,
    ): PlaylistScheduler = PlaylistScheduler(context)

    @Provides
    @Singleton
    fun playlistRepository(
        @ApplicationContext context: Context,
        db: AkVodDatabase,
        cipher: SecretCipher,
        xtream: XtreamClient,
        importer: PlaylistImporter,
        scheduler: PlaylistScheduler,
        indexer: CatalogIndexer,
    ): PlaylistRepository = PlaylistRepository(context, db, cipher, xtream, importer, scheduler, indexer)

    @Provides
    @Singleton
    fun tmdbClient(client: OkHttpClient): TmdbClient = TmdbClient(client)

    @Provides
    @Singleton
    fun tmdbSettings(
        @ApplicationContext context: Context,
        cipher: SecretCipher,
    ): TmdbSettings = TmdbSettings(context, cipher)

    @Provides
    @Singleton
    fun titleMatcher(
        db: AkVodDatabase,
        tmdb: TmdbClient,
        settings: TmdbSettings,
    ): TitleMatcher = TitleMatcher(db, tmdb, settings)

    @Provides
    @Singleton
    fun matchScheduler(
        @ApplicationContext context: Context,
    ): MatchScheduler = MatchScheduler(context)

    @Provides
    @Singleton
    fun catalogRepository(
        db: AkVodDatabase,
        tmdb: TmdbClient,
        settings: TmdbSettings,
        scheduler: MatchScheduler,
    ): CatalogRepository = CatalogRepository(db, tmdb, settings, scheduler)

    @Provides
    @Singleton
    fun playlistWorkerFactory(
        importer: PlaylistImporter,
        db: AkVodDatabase,
        matcher: TitleMatcher,
        matchScheduler: MatchScheduler,
        indexer: CatalogIndexer,
        imdbUpdater: ImdbRatingsUpdater,
        imdbSettings: ImdbRatingsSettings,
        imdbScheduler: ImdbRatingsScheduler,
    ): PlaylistWorkerFactory =
        PlaylistWorkerFactory(
            importer,
            db.playlistDao(),
            matcher,
            matchScheduler,
            indexer,
            imdbUpdater,
            imdbSettings,
            imdbScheduler,
        )

    @Provides
    @Singleton
    fun imdbRatingsSettings(
        @ApplicationContext context: Context,
    ): ImdbRatingsSettings = ImdbRatingsSettings(context)

    @Provides
    @Singleton
    fun imdbRatingsScheduler(
        @ApplicationContext context: Context,
        settings: ImdbRatingsSettings,
    ): ImdbRatingsScheduler = ImdbRatingsScheduler(context, settings)

    @Provides
    @Singleton
    fun imdbRatingsUpdater(
        db: AkVodDatabase,
        http: OkHttpClient,
        settings: ImdbRatingsSettings,
        indexer: CatalogIndexer,
    ): ImdbRatingsUpdater = ImdbRatingsUpdater(db, http, settings, indexer)

    @Provides
    @Singleton
    fun catalogIndexer(
        @ApplicationContext context: Context,
        db: AkVodDatabase,
    ): CatalogIndexer = CatalogIndexer(context, db)

    @Provides
    @Singleton
    fun hubRepository(
        db: AkVodDatabase,
        indexer: CatalogIndexer,
        importer: PlaylistImporter,
        tmdb: TmdbClient,
        settings: TmdbSettings,
        playback: PlaybackSettings,
    ): HubRepository = HubRepository(db, indexer, importer, tmdb, settings, playback)

    @Provides
    @Singleton
    fun playbackSettings(
        @ApplicationContext context: Context,
    ): PlaybackSettings = PlaybackSettings(context)

    @Provides
    @Singleton
    fun stremioClient(client: OkHttpClient): StremioClient = StremioClient(client)

    @Provides
    @Singleton
    fun stremioAddons(
        @ApplicationContext context: Context,
    ): StremioAddons = StremioAddons(context)

    @Provides
    @Singleton
    fun subtitleRepository(
        client: StremioClient,
        addons: StremioAddons,
    ): SubtitleRepository = SubtitleRepository(client, addons)

    @Provides
    @Singleton
    fun discoverRepository(
        db: AkVodDatabase,
        tmdb: TmdbClient,
        settings: TmdbSettings,
        stremio: StremioClient,
        addons: StremioAddons,
    ): DiscoverRepository = DiscoverRepository(db, tmdb, settings, stremio, addons)

    @Provides
    @Singleton
    fun addonInstaller(
        client: StremioClient,
        addons: StremioAddons,
    ): AddonInstaller = AddonInstaller(client, addons)

    @Provides
    @Singleton
    fun settingsBackup(
        playlists: PlaylistRepository,
        catalog: CatalogRepository,
        addons: StremioAddons,
        playback: PlaybackSettings,
    ): SettingsBackup = SettingsBackup(playlists, catalog, addons, playback)

    @Provides
    @Singleton
    fun phoneSetupServer(
        playlists: PlaylistRepository,
        catalog: CatalogRepository,
        installer: AddonInstaller,
        backup: SettingsBackup,
    ): PhoneSetupServer = PhoneSetupServer(playlists, catalog, installer, backup)
}
