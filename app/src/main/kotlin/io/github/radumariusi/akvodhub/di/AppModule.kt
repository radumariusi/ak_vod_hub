package io.github.radumariusi.akvodhub.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.radumariusi.akvodhub.BuildConfig
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.playlist.PlaylistRepository
import io.github.radumariusi.akvodhub.data.playlist.PlaylistScheduler
import io.github.radumariusi.akvodhub.data.playlist.PlaylistWorkerFactory
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.security.KeystoreSecretCipher
import io.github.radumariusi.akvodhub.data.security.SecretCipher
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
    ): PlaylistRepository = PlaylistRepository(context, db, cipher, xtream, importer, scheduler)

    @Provides
    @Singleton
    fun playlistWorkerFactory(
        importer: PlaylistImporter,
        db: AkVodDatabase,
    ): PlaylistWorkerFactory = PlaylistWorkerFactory(importer, db.playlistDao())
}
