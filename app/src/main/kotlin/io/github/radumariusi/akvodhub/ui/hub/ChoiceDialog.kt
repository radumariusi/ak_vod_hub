@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.ui.theme.ThemedPanel
import io.github.radumariusi.akvodhub.ui.touch.ListItem

/** O alegere dintr-o listă (filtru, sursă de redare), cu focusul pe opțiunea curentă. */
data class Choice(
    val title: String,
    val options: List<String>,
    val selected: Int?,
    val headings: Map<Int, String> = emptyMap(),
    val footer: String? = null,
    val onPick: (Int) -> Unit,
)

@Composable
fun ChoiceDialog(
    choice: Choice,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val initial = (choice.selected ?: 0).coerceIn(0, (choice.options.size - 1).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(choice) { runCatching { focus.requestFocus() } }
        ThemedPanel(Modifier.width(520.dp)) {
            val colors = MaterialTheme.colorScheme
            val itemColors =
                ListItemDefaults.colors(
                    selectedContainerColor = colors.surfaceVariant,
                    selectedContentColor = colors.onSurface,
                    focusedSelectedContainerColor = colors.secondary,
                    focusedSelectedContentColor = colors.onSecondary,
                    pressedSelectedContainerColor = colors.secondary,
                    pressedSelectedContentColor = colors.onSecondary,
                )
            Text(choice.title, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = initial),
                modifier = Modifier.heightIn(max = 400.dp).weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(choice.options) { index, label ->
                    Column {
                        choice.headings[index]?.let { labelHeading ->
                            Text(
                                labelHeading,
                                modifier = Modifier.fillMaxWidth().semantics { heading() }.padding(12.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ListItem(
                            selected = index == choice.selected,
                            colors = itemColors,
                            scale = ListItemDefaults.scale(focusedScale = 1f),
                            onClick = {
                                choice.onPick(index)
                                onDismiss()
                            },
                            headlineContent = { Text(label) },
                            modifier = if (index == initial) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
                choice.footer?.let { message ->
                    item {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
