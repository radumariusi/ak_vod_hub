package io.github.radumariusi.akvodhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.hub.BrowseViewModel
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.hub.HomeViewModel
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.ui.AppRoot
import io.github.radumariusi.akvodhub.ui.HubViewModels
import io.github.radumariusi.akvodhub.ui.theme.AkVodHubTheme
import io.github.radumariusi.akvodhub.update.UpdateViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val updateViewModel: UpdateViewModel by viewModels()
    private val playlistsViewModel: PlaylistsViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val browseViewModel: BrowseViewModel by viewModels()
    private val detailsViewModel: DetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) updateViewModel.checkOnStartup()
        setContent {
            AkVodHubTheme {
                AppRoot(
                    updateViewModel,
                    playlistsViewModel,
                    tmdbViewModel,
                    HubViewModels(homeViewModel, browseViewModel, detailsViewModel),
                )
            }
        }
    }
}
