package io.github.radumariusi.akvodhub

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.github.radumariusi.akvodhub.data.playlist.PlaylistWorkerFactory
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class AkVodHubApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: PlaylistWorkerFactory

    @Inject lateinit var okHttpClient: OkHttpClient

    // WorkManager se inițializează la prima folosire, cu fabrica noastră de worker-e (vezi AndroidManifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Coil (postere, fundaluri): același client HTTP ca restul aplicației; cache în memorie și pe disc implicite.
    // Fără crossfade: pe TV-uri slabe, derularea rapidă prin rânduri rămâne fluidă.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
            .crossfade(false)
            .build()
}
