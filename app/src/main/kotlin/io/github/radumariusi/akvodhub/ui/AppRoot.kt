@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.BrowseViewModel
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.hub.HomeViewModel
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.remote.PhoneSetupViewModel
import io.github.radumariusi.akvodhub.tv.NavRequest
import io.github.radumariusi.akvodhub.ui.hub.BrowseScreen
import io.github.radumariusi.akvodhub.ui.hub.DetailsScreen
import io.github.radumariusi.akvodhub.ui.hub.HomeScreen
import io.github.radumariusi.akvodhub.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

enum class Destination(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    HOME("Acasă", R.drawable.ic_home),
    MOVIES("Filme", R.drawable.ic_movie),
    SERIES("Seriale", R.drawable.ic_tv),
    SEARCH("Căutare", R.drawable.ic_search),
    SETTINGS("Setări", R.drawable.ic_settings),
}

/** ViewModel-urile ecranelor hub-ului. */
class HubViewModels(
    val home: HomeViewModel,
    val browse: BrowseViewModel,
    val details: DetailsViewModel,
    val playback: PlaybackSettingsViewModel,
    val phoneSetup: PhoneSetupViewModel,
)

/**
 * Navigarea: meniul lateral (Acasă, Filme, Seriale, Căutare, Setări) și pagina de detalii peste tot ecranul.
 * La „Înapoi” din detalii, ecranul de dinainte revine cu aceeași derulare, iar focusul pe cardul deschis.
 */
@Composable
fun AppRoot(
    updateViewModel: UpdateViewModel,
    playlistsViewModel: PlaylistsViewModel,
    tmdbViewModel: TmdbViewModel,
    hub: HubViewModels,
    navRequests: MutableStateFlow<NavRequest?>,
) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    var openTitle by rememberSaveable { mutableStateOf<Long?>(null) }

    // Titlurile deschise din pagina de detalii (recomandate, colecție…): „Înapoi” revine la cel dinainte.
    var titleStack by rememberSaveable { mutableStateOf(LongArray(0)) }
    var openedFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    val updateState by updateViewModel.state.collectAsState()
    val showDialog by updateViewModel.showDialog.collectAsState()
    val totals by playlistsViewModel.totals.collectAsState()
    val languages by hub.browse.languages.collectAsState()
    val states = rememberSaveableStateHolder()

    val open: (TitleCard, String) -> Unit = { card, key ->
        openedFrom = key
        titleStack = LongArray(0)
        openTitle = card.id
    }
    val onRestored = { restoreKey = null }

    // Din afara aplicației: fișa unui titlu (ecranul principal, căutarea globală) sau o căutare (vocală).
    val navRequest by navRequests.collectAsState()
    LaunchedEffect(navRequest) {
        when (val request = navRequest) {
            null -> {
                return@LaunchedEffect
            }

            is NavRequest.OpenTitle -> {
                titleStack = LongArray(0)
                openedFrom = null
                openTitle = request.titleId
            }

            // Redarea o pornește MainActivity; aici rămâne doar fișa dedesubt.
            is NavRequest.Play -> {
                titleStack = LongArray(0)
                openedFrom = null
                openTitle = request.titleId
            }

            is NavRequest.Search -> {
                openTitle = null
                titleStack = LongArray(0)
                current = Destination.SEARCH
                val search = hub.browse.search
                search.setFilter(
                    search.state.value.filter
                        .copy(text = request.text),
                )
            }
        }
        navRequests.value = null
    }
    // Dacă cardul nu mai e pe ecran (listă reîncărcată), nu „furăm” focusul mai târziu.
    LaunchedEffect(restoreKey) {
        if (restoreKey != null) {
            delay(1_500)
            restoreKey = null
        }
    }

    val titleId = openTitle
    if (titleId != null) {
        BackHandler {
            if (titleStack.isNotEmpty()) {
                openTitle = titleStack.last()
                titleStack = titleStack.copyOf(titleStack.size - 1)
            } else {
                openTitle = null
                restoreKey = openedFrom
            }
        }
        DetailsScreen(hub.details, titleId) { card ->
            if (card.id != titleId) {
                titleStack = (titleStack + titleId).takeLast(MAX_TITLE_STACK).toLongArray()
                openTitle = card.id
            }
        }
    } else {
        BackHandler(enabled = current != Destination.HOME) { current = Destination.HOME }
        states.SaveableStateProvider("main") {
            NavigationDrawer(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                drawerContent = {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
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
                Box(Modifier.fillMaxSize().padding(start = 24.dp, top = 28.dp, end = 32.dp, bottom = 8.dp)) {
                    // Fiecare ecran își păstrează derularea când treci prin meniu sau prin detalii.
                    states.SaveableStateProvider(current.name) {
                        when (current) {
                            Destination.HOME -> {
                                HomeScreen(
                                    totals = totals,
                                    viewModel = hub.home,
                                    onOpenSettings = { current = Destination.SETTINGS },
                                    onOpen = open,
                                    restoreKey = restoreKey,
                                    onRestored = onRestored,
                                )
                            }

                            Destination.MOVIES -> {
                                BrowseScreen(
                                    "Filme",
                                    hub.browse.movies,
                                    languages,
                                    false,
                                    "movies",
                                    open,
                                    restoreKey,
                                    onRestored,
                                )
                            }

                            Destination.SERIES -> {
                                BrowseScreen(
                                    "Seriale",
                                    hub.browse.series,
                                    languages,
                                    false,
                                    "series",
                                    open,
                                    restoreKey,
                                    onRestored,
                                )
                            }

                            Destination.SEARCH -> {
                                BrowseScreen(
                                    "Căutare",
                                    hub.browse.search,
                                    languages,
                                    true,
                                    "search",
                                    open,
                                    restoreKey,
                                    onRestored,
                                )
                            }

                            Destination.SETTINGS -> {
                                SettingsScreen(
                                    updateViewModel,
                                    updateState,
                                    playlistsViewModel,
                                    tmdbViewModel,
                                    hub.playback,
                                    hub.phoneSetup,
                                )
                            }
                        }
                    }
                }
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

private const val MAX_TITLE_STACK = 20
