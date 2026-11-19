package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import io.github.radumariusi.akvodhub.data.catalog.TitleFacts
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPerson
import java.util.Calendar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.hub.ActorState
import io.github.radumariusi.akvodhub.hub.ActorViewModel
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ActorScreen(
    viewModel: ActorViewModel,
    personId: Long,
    name: String,
    onBack: () -> Unit,
    onOpen: (TitleCard) -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val state = snapshot.takeIf { it.personId == personId } ?: ActorState(personId)
    val grid = rememberLazyGridState()
    var opened by rememberSaveable(personId) { mutableStateOf<Long?>(null) }
    var requestFocus by rememberSaveable(personId) { mutableStateOf(true) }
    LaunchedEffect(personId) { viewModel.open(personId, restoreThrough = opened) }
    LaunchedEffect(grid, personId) {
        snapshotFlow {
            val visible = grid.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: -1) to (visible.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisible(first, last) }
    }
    LaunchedEffect(opened, requestFocus, state.items) {
        if (!requestFocus) return@LaunchedEffect
        val index = state.items.indexOfFirst { it.id == opened }
        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == opened }) {
            grid.scrollToItem(index)
        }
    }
    val profile by viewModel.profile.collectAsState()
    Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val photo = Images.profile(profile?.takeIf { it.id == personId }?.profilePath)
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(104.dp, 156.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.headlineMedium)
                profile?.takeIf { it.id == personId }?.let { person ->
                    personLines(person).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    person.biography?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text("Filme și seriale disponibile în playlist", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onBack) { Text("Înapoi la fișă") }
            }
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { viewModel.open(personId, true) }) { Text("Încearcă din nou") }
        }
        if (state.loading && state.items.isEmpty()) Text("Se încarcă filmografia…")
        if (!state.loading && state.items.isEmpty() &&
            state.error == null
        ) {
            Text("Nu există titluri disponibile în playlist.")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(PosterWidth + 16.dp),
            state = grid,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(10.dp),
        ) {
            itemsIndexed(state.items, key = { _, card -> card.id }) { _, card ->
                PosterCard(
                    card,
                    onClick = {
                        opened = card.id
                        requestFocus = true
                        onOpen(card)
                    },
                    onFocused = {},
                    requestFocus = requestFocus && card.id == (opened ?: state.items.firstOrNull()?.id),
                    onFocusRequested = { requestFocus = false },
                )
            }
        }
    }
}

/** „Născut pe 30 ian 1967 (vârsta 59)”, locul nașterii, meseria. */
private fun personLines(person: TmdbPerson): List<String> {
    val born = TitleFacts.date(person.birthday)
    val died = TitleFacts.date(person.deathday)
    val age = age(person.birthday, person.deathday)
    val birth =
        born?.let {
            buildString {
                append("Născut pe ").append(it)
                if (died != null) append(" · decedat pe ").append(died)
                age?.let { years -> append(" (vârsta ").append(years).append(")") }
            }
        }
    val department =
        when (person.department) {
            "Acting" -> "Actorie"
            "Directing" -> "Regie"
            "Writing" -> "Scenariu"
            "Production" -> "Producție"
            "Sound" -> "Muzică"
            "Camera" -> "Imagine"
            "Editing" -> "Montaj"
            else -> person.department
        }
    return listOfNotNull(
        birth,
        person.placeOfBirth?.takeIf { it.isNotBlank() },
        department?.let { "Cunoscut pentru: $it" },
    )
}

private fun age(birthday: String?, deathday: String?): Int? {
    fun parts(value: String?) = value?.split('-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }
    val birth = parts(birthday) ?: return null
    val end =
        parts(deathday) ?: Calendar.getInstance().let {
            listOf(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
    var years = end[0] - birth[0]
    if (end[1] < birth[1] || (end[1] == birth[1] && end[2] < birth[2])) years--
    return years.takeIf { it in 0..130 }
}
