@file:OptIn(ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui.touch

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
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
): Modifier {
    val click by rememberUpdatedState(onClick)
    val active by rememberUpdatedState(enabled)
    return Modifier.pointerInput(Unit) { detectTapGestures { if (active) click() } }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = TvButton(onClick = onClick, modifier = modifier.then(tap(enabled, onClick)), enabled = enabled, content = content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = TvOutlinedButton(
    onClick = onClick,
    modifier = modifier.then(tap(enabled, onClick)),
    enabled = enabled,
    content = content,
)

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = TvSurface(onClick = onClick, modifier = modifier.then(tap(true, onClick)), content = content)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: CardScale = CardDefaults.scale(),
    content: @Composable ColumnScope.() -> Unit,
) = TvCard(onClick = onClick, modifier = modifier.then(tap(true, onClick)), scale = scale, content = content)

@Composable
fun ListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) = TvListItem(
    selected = selected,
    onClick = onClick,
    headlineContent = headlineContent,
    modifier = modifier.then(tap(true, onClick)),
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
