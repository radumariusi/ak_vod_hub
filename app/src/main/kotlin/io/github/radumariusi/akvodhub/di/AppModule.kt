package io.github.radumariusi.akvodhub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.radumariusi.akvodhub.BuildConfig
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
}
