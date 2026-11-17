package io.github.radumariusi.akvodhub

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.github.radumariusi.akvodhub.data.catalog.MatchScheduler
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsScheduler
import io.github.radumariusi.akvodhub.data.playlist.PlaylistWorkerFactory
import io.github.radumariusi.akvodhub.tv.HomeScreenPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class AkVodHubApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject lateinit var imdbScheduler: ImdbRatingsScheduler

    @Inject lateinit var matchScheduler: MatchScheduler

    @Inject lateinit var workerFactory: PlaylistWorkerFactory

    @Inject lateinit var okHttpClient: OkHttpClient

    @Inject lateinit var homeScreen: HomeScreenPublisher

    /** Lucrări care țin cât procesul: ecranul principal Android TV (Watch Next, canalul aplicației). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        homeScreen.start(appScope)
        matchScheduler.enqueue()
        imdbScheduler.ensureScheduled()
    }

    // WorkManager se inițializează la prima folosire, cu fabrica noastră de worker-e (vezi AndroidManifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Coil (postere, fundaluri): același client HTTP ca restul aplicației; plafon pentru cache-ul de pe disc.
    // Fără crossfade: pe TV-uri slabe, derularea rapidă prin rânduri rămâne fluidă.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }.crossfade(false)
            .build()
}
