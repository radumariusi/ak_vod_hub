@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.R
import io.github.radumariusi.akvodhub.catalog.TmdbViewModel
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.ActorViewModel
import io.github.radumariusi.akvodhub.hub.BrowseViewModel
import io.github.radumariusi.akvodhub.hub.DetailsViewModel
import io.github.radumariusi.akvodhub.hub.HomeViewModel
import io.github.radumariusi.akvodhub.hub.WatchlistViewModel
import io.github.radumariusi.akvodhub.player.PlaybackSettingsViewModel
import io.github.radumariusi.akvodhub.playlist.PlaylistsViewModel
import io.github.radumariusi.akvodhub.remote.PhoneSetupViewModel
import io.github.radumariusi.akvodhub.tv.NavRequest
import io.github.radumariusi.akvodhub.ui.hub.ActorScreen
import io.github.radumariusi.akvodhub.ui.hub.BrowseScreen
import io.github.radumariusi.akvodhub.ui.hub.DetailsScreen
import io.github.radumariusi.akvodhub.ui.hub.HomeScreen
import io.github.radumariusi.akvodhub.ui.hub.WatchlistScreen
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
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
    WATCHLIST("Lista mea", R.drawable.ic_movie),
    SEARCH("Caută", R.drawable.ic_search),
    SETTINGS("Setări", R.drawable.ic_settings),
}

/** ViewModel-urile ecranelor hub-ului. */
class HubViewModels(
    val home: HomeViewModel,
    val browse: BrowseViewModel,
    val details: DetailsViewModel,
    val playback: PlaybackSettingsViewModel,
    val phoneSetup: PhoneSetupViewModel,
    val actor: ActorViewModel,
    val watchlist: WatchlistViewModel,
)

/**
 * Navigarea: filele de sus (Acasă, Filme, Seriale, Lista mea, Caută, Setări) și pagina de detalii peste tot ecranul.
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
    var actors by rememberSaveable { mutableStateOf(arrayListOf<ActorFrame>()) }

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
        actors = arrayListOf()
        titleStack = LongArray(0)
        openTitle = card.id
    }
    val onRestored = { restoreKey = null }

    // Din afara aplicației: fișa unui titlu (ecranul principal, căutarea globală) sau o căutare (vocală).
    val navRequest by navRequests.collectAsState()
    LaunchedEffect(navRequest) {
        if (navRequest != null) actors = arrayListOf()
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
                if (actors.isEmpty()) restoreKey = openedFrom
            }
        }
        DetailsScreen(hub.details, titleId, onActor = { person ->
            actors =
                ArrayList((actors + ActorFrame(person.id, person.name, titleId, titleStack)).takeLast(MAX_TITLE_STACK))
            titleStack = LongArray(0)
            openTitle = null
        }) { card ->
            if (card.id != titleId) {
                titleStack = (titleStack + titleId).takeLast(MAX_TITLE_STACK).toLongArray()
                openTitle = card.id
            }
        }
    } else if (actors.isNotEmpty()) {
        val actor = actors.last()
        val back = {
            actors = ArrayList(actors.dropLast(1))
            titleStack = actor.titleStack
            openTitle = actor.origin
        }
        BackHandler(onBack = back)
        states.SaveableStateProvider("actor:${actor.id}") {
            ActorScreen(hub.actor, actor.id, actor.name, onBack = back) { card -> openTitle = card.id }
        }
    } else {
        BackHandler(enabled = current != Destination.HOME) { current = Destination.HOME }
        states.SaveableStateProvider("main") {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    items(Destination.entries, key = { it.name }) { destination ->
                        val tabModifier =
                            Modifier.semantics {
                                selected = current == destination
                                role = Role.Tab
                            }
                        OutlinedButton(onClick = { current = destination }, modifier = tabModifier) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    destination.label,
                                    textDecoration = if (current == destination) TextDecoration.Underline else null,
                                )
                                Box(
                                    Modifier.width(32.dp).height(3.dp).background(
                                        if (current ==
                                            destination
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).padding(start = 32.dp, top = 8.dp, end = 32.dp, bottom = 8.dp)) {
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

                            Destination.WATCHLIST -> {
                                WatchlistScreen(hub.watchlist, open, restoreKey, onRestored)
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

/** Contextul unei pagini de actor, inclusiv întoarcerea prin fișe/actori deschiși succesiv. */
private data class ActorFrame(
    val id: Long,
    val name: String,
    val origin: Long,
    val titleStack: LongArray,
) : java.io.Serializable {
    private companion object {
        const val serialVersionUID = 1L
    }
}
