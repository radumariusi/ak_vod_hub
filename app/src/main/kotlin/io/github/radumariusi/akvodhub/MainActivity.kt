package io.github.radumariusi.akvodhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.ui.AppRoot
import io.github.radumariusi.akvodhub.ui.theme.AkVodHubTheme
import io.github.radumariusi.akvodhub.update.UpdateViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val updateViewModel: UpdateViewModel by viewModels()
    private val playlistsViewModel: PlaylistsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) updateViewModel.checkOnStartup()
        setContent {
            AkVodHubTheme {
                AppRoot(updateViewModel, playlistsViewModel)
            }
        }
    }
}
