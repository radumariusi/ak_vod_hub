package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.data.update.AppUpdate
import io.github.radumariusi.akvodhub.ui.touch.Button
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton
import io.github.radumariusi.akvodhub.update.UpdateState

/** Update-ul oferit utilizatorului în starea curentă, dacă există. */
fun UpdateState.offeredUpdate(): AppUpdate? =
    when (this) {
        is UpdateState.Available -> update
        is UpdateState.NeedsPermission -> update
        else -> null
    }

@Composable
fun UpdateStatusText(state: UpdateState) {
    val text =
        when (state) {
            UpdateState.Idle -> {
                null
            }

            UpdateState.Checking -> {
                "Se verifică GitHub…"
            }

            UpdateState.UpToDate -> {
                "Ai ultima versiune."
            }

            is UpdateState.Available -> {
                "Versiune nouă disponibilă: ${state.update.version}"
            }

            is UpdateState.Downloading -> {
                "Se descarcă ${state.update.version}… ${(state.progress * 100).toInt()}%"
            }

            is UpdateState.Installing -> {
                "Se instalează ${state.update.version}…"
            }

            is UpdateState.NeedsPermission -> {
                "Permite „Instalează aplicații necunoscute” pentru AK VOD Hub (Setări Android > Securitate), " +
                    "apoi apasă din nou Instalează."
            }

            is UpdateState.Failed -> {
                state.message
            }
        }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: (AppUpdate) -> Unit,
    onDismiss: () -> Unit,
) {
    val update =
        when (state) {
            is UpdateState.Available -> state.update
            is UpdateState.Downloading -> state.update
            is UpdateState.Installing -> state.update
            is UpdateState.NeedsPermission -> state.update
            else -> null
        }
    if (update == null && state !is UpdateState.Failed) return

    val focus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(560.dp),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (update != null) "Versiune nouă: ${update.version}" else "Update",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (update != null && update.notes.isNotBlank()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                UpdateStatusText(state)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val offered = state.offeredUpdate()
                    if (offered != null) {
                        Button(onClick = { onInstall(offered) }, modifier = Modifier.focusRequester(focus)) {
                            Text("Actualizează")
                        }
                        OutlinedButton(onClick = onDismiss) { Text("Mai târziu") }
                    } else {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) {
                            Text("Închide")
                        }
                    }
                }
            }
        }
    }
}
