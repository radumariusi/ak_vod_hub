package io.github.radumariusi.akvodhub

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.radumariusi.akvodhub.data.playlist.PlaylistWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class AkVodHubApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: PlaylistWorkerFactory

    // WorkManager se inițializează la prima folosire, cu fabrica noastră de worker-e (vezi AndroidManifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
