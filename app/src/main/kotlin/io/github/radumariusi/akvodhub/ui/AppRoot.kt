@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.R
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.update.UpdateViewModel

enum class Destination(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    HOME("Acasă", R.drawable.ic_home),
    SEARCH("Căutare", R.drawable.ic_search),
    SETTINGS("Setări", R.drawable.ic_settings),
}

@Composable
fun AppRoot(
    updateViewModel: UpdateViewModel,
    playlistsViewModel: PlaylistsViewModel,
) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    val updateState by updateViewModel.state.collectAsState()
    val showDialog by updateViewModel.showDialog.collectAsState()
    val totals by playlistsViewModel.totals.collectAsState()

    NavigationDrawer(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        drawerContent = {
            Column(
                modifier = Modifier.fillMaxHeight().padding(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Destination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = current == destination,
                        onClick = { current = destination },
                        leadingContent = {
                            Icon(painter = painterResource(destination.icon), contentDescription = null)
                        },
                    ) {
                        Text(destination.label)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 32.dp)) {
            when (current) {
                Destination.HOME -> HomeScreen(totals, onOpenSettings = { current = Destination.SETTINGS })
                Destination.SEARCH -> SearchScreen()
                Destination.SETTINGS -> SettingsScreen(updateViewModel, updateState, playlistsViewModel)
            }
        }
    }

    if (showDialog) {
        UpdateDialog(
            state = updateState,
            onInstall = updateViewModel::downloadAndInstall,
            onDismiss = updateViewModel::dismissDialog,
        )
    }
}
