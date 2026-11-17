package io.github.radumariusi.akvodhub.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.playlist.AddPlaylistState

@Composable
fun AddPlaylistScreen(
    state: AddPlaylistState,
    onAddUrl: (name: String, url: String) -> Unit,
    onAddFile: (name: String, uri: Uri) -> Unit,
    onAddXtream: (name: String, server: String, user: String, password: String) -> Unit,
    onClose: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(PlaylistType.XTREAM) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var file by remember { mutableStateOf<Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) file = uri
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state) { if (state == AddPlaylistState.Done) onClose() }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text("Adaugă playlist", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                PlaylistType.XTREAM to "Cont Xtream",
                PlaylistType.M3U_URL to "Link M3U",
                PlaylistType.M3U_FILE to "Fișier M3U",
            ).forEachIndexed { index, (option, label) ->
                val modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier
                if (type == option) {
                    Button(onClick = {}, modifier = modifier) { Text(label) }
                } else {
                    OutlinedButton(onClick = {
                        type = option
                        localError = null
                    }, modifier = modifier) { Text(label) }
                }
            }
        }

        TvTextField(name, { name = it }, label = "Nume (opțional)", placeholder = "ex. Furnizorul meu")
        when (type) {
            PlaylistType.XTREAM -> {
                TvTextField(
                    server,
                    { server = it },
                    label = "Server",
                    placeholder = "ex. http://exemplu.com:8080",
                    keyboardType = KeyboardType.Uri,
                )
                TvTextField(user, { user = it }, label = "Utilizator")
                TvTextField(password, { password = it }, label = "Parolă", password = true, imeAction = ImeAction.Done)
            }

            PlaylistType.M3U_URL -> {
                TvTextField(
                    url,
                    { url = it },
                    label = "Link M3U",
                    placeholder = "http://…/get.php?username=…&password=…&type=m3u_plus",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                )
            }

            PlaylistType.M3U_FILE -> {
                Text(
                    file?.lastPathSegment?.let { "Fișier ales: $it" } ?: "Niciun fișier ales.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = {
                    try {
                        picker.launch(arrayOf("*/*"))
                    } catch (_: ActivityNotFoundException) {
                        localError =
                            "Televizorul nu are un selector de fișiere. Folosește un link M3U sau un cont Xtream."
                    }
                }) { Text("Alege fișierul") }
            }
        }

        val message =
            when {
                localError != null -> localError
                state is AddPlaylistState.Error -> state.message
                state == AddPlaylistState.Saving && type == PlaylistType.XTREAM -> "Se verifică contul…"
                state == AddPlaylistState.Saving -> "Se salvează…"
                else -> null
            }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (state is AddPlaylistState.Error || localError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = state != AddPlaylistState.Saving,
                onClick = {
                    localError = null
                    when (type) {
                        PlaylistType.XTREAM -> {
                            if (server.isBlank() || user.isBlank() || password.isBlank()) {
                                localError = "Completează serverul, utilizatorul și parola."
                            } else {
                                onAddXtream(name, server, user, password)
                            }
                        }

                        PlaylistType.M3U_URL -> {
                            if (url.isBlank()) localError = "Completează linkul M3U." else onAddUrl(name, url)
                        }

                        PlaylistType.M3U_FILE -> {
                            val chosen = file
                            if (chosen == null) localError = "Alege mai întâi un fișier." else onAddFile(name, chosen)
                        }
                    }
                },
            ) { Text("Salvează și importă") }
            OutlinedButton(onClick = onClose) { Text("Renunță") }
        }
    }
}
