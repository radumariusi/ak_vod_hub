package io.github.radumariusi.akvodhub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.hub.ActorViewModel
import io.github.radumariusi.akvodhub.hub.BrowseViewModel
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.hub.HomeViewModel
import io.github.radumariusi.akvodhub.hub.WatchlistViewModel
import io.github.radumariusi.akvodhub.player.PlayRequest
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.player.PlayerActivity
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.remote.PhoneSetupViewModel
import io.github.radumariusi.akvodhub.tv.DeepLinks
import io.github.radumariusi.akvodhub.tv.NavRequest
import io.github.radumariusi.akvodhub.ui.AppRoot
import io.github.radumariusi.akvodhub.ui.HubViewModels
import io.github.radumariusi.akvodhub.ui.hub.LocalPosterBadges
import io.github.radumariusi.akvodhub.ui.theme.AkVodHubTheme
import io.github.radumariusi.akvodhub.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val updateViewModel: UpdateViewModel by viewModels()
    private val playlistsViewModel: PlaylistsViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val actorViewModel: ActorViewModel by viewModels()
    private val watchlistViewModel: WatchlistViewModel by viewModels()
    private val browseViewModel: BrowseViewModel by viewModels()
    private val detailsViewModel: DetailsViewModel by viewModels()
    private val playbackViewModel: PlaybackSettingsViewModel by viewModels()
    private val phoneSetupViewModel: PhoneSetupViewModel by viewModels()

    /** Cererea venită din afară (ecranul principal, căutarea globală), consumată de [AppRoot]. */
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            updateViewModel.checkOnStartup()
            handle(intent)
        }
        setContent {
            AkVodHubTheme {
                // Pe telefon conținutul nu intră sub barele sistemului / tastatură; pe TV marginile sunt 0.
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CompositionLocalProvider(
                        LocalPosterBadges provides
                            playbackViewModel.prefs
                                .collectAsState()
                                .value.posterBadges,
                    ) {
                        AppRoot(
                            updateViewModel,
                            playlistsViewModel,
                            tmdbViewModel,
                            HubViewModels(
                                homeViewModel,
                                browseViewModel,
                                detailsViewModel,
                                playbackViewModel,
                                phoneSetupViewModel,
                                actorViewModel,
                                watchlistViewModel,
                            ),
                            navRequests,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (val request = DeepLinks.parse(intent)) {
            null -> {
                Unit
            }

            is NavRequest.Play -> {
                // Din „Watch Next”: fișa titlului rămâne dedesubt, iar playerul pornește de unde ai rămas.
                navRequests.value = NavRequest.OpenTitle(request.titleId)
                startActivity(PlayerActivity.intent(this, PlayRequest(request.titleId, resume = true)))
            }

            else -> {
                navRequests.value = request
            }
        }
    }
}
