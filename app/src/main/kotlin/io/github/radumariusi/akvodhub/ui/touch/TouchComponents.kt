@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.touch

import android.view.ViewConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.OutlinedIconButtonDefaults
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.OutlinedIconButton as TvOutlinedIconButton
import androidx.tv.material3.Surface as TvSurface

/*
 * Componentele din tv-material reacționează doar la telecomandă (OK/Enter), nu și la atingeri.
 * Variantele de aici sunt aceleași componente, plus o atingere care apelează același onClick,
 * ca aplicația să poată fi folosită și pe telefon. Pe TV comportamentul rămâne identic.
 */

/** O atingere (fără derulare) pe element apelează [onClick]. */
@Composable
private fun tap(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val active by rememberUpdatedState(enabled)
    val hasLongClick = onLongClick != null
    return Modifier.pointerInput(hasLongClick) {
        detectTapGestures(
            onLongPress = if (hasLongClick) ({ _ -> if (active) longClick?.invoke() }) else null,
            onTap = { if (active) click() },
        )
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TvButton(
        onClick = onClick,
        modifier = modifier.then(tap(enabled, onClick)),
        enabled = enabled,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors =
            ButtonDefaults.colors(
                contentColor = colors.onSurface,
                focusedContainerColor = colors.secondary,
                focusedContentColor = colors.onSecondary,
            ),
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = OutlinedButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TvOutlinedButton(
        onClick = onClick,
        modifier = modifier.then(tap(enabled, onClick)),
        enabled = enabled,
        contentPadding = contentPadding,
        scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
        colors =
            OutlinedButtonDefaults.colors(
                contentColor = colors.onSurface,
                focusedContainerColor = colors.secondary,
                focusedContentColor = colors.onSecondary,
            ),
        content = content,
    )
}

@Composable
fun OutlinedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TvOutlinedIconButton(
        onClick = onClick,
        modifier = modifier.then(tap(true, onClick)),
        scale = OutlinedIconButtonDefaults.scale(focusedScale = 1f),
        colors =
            OutlinedIconButtonDefaults.colors(
                focusedContainerColor = colors.secondary,
                focusedContentColor = colors.onSecondary,
            ),
        content = content,
    )
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TvSurface(
        onClick = onClick,
        modifier = modifier.then(tap(true, onClick)),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
                focusedContainerColor = colors.surfaceVariant,
                focusedContentColor = colors.onSurface,
                pressedContentColor = colors.onSurface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, colors.secondary), inset = 0.dp, shape = shape),
            ),
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    TvCard(
        onClick = onClick,
        modifier =
            modifier
                .then(if (onLongClick == null) Modifier else cardKeyActions(onClick, onLongClick))
                .then(tap(true, onClick, onLongClick)),
        onLongClick = onLongClick,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1f),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
                        inset = 0.dp,
                        shape = shape,
                    ),
            ),
        content = content,
    )
}

/** Mută focusul abia la eliberarea OK, astfel încât butonul destinație să nu primească aceeași apăsare. */
private fun cardKeyActions(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    Modifier.onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) {
                    val elapsed = event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime
                    if (elapsed >= ViewConfiguration.getLongPressTimeout()) onLongClick() else onClick()
                }
                true
            }

            else -> {
                false
            }
        }
    }

@Composable
fun ListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors =
        ListItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
            focusedContentColor = MaterialTheme.colorScheme.onSecondary,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.secondary,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    scale: ListItemScale = ListItemDefaults.scale(focusedScale = 1f),
) = TvListItem(
    selected = selected,
    onClick = onClick,
    headlineContent = headlineContent,
    modifier = modifier.then(tap(true, onClick)),
    colors = colors,
    scale = scale,
)

@Composable
fun NavigationDrawerScope.NavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = TvNavigationDrawerItem(
    selected = selected,
    onClick = onClick,
    leadingContent = leadingContent,
    modifier = modifier.then(tap(true, onClick)),
    content = content,
)
