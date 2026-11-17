@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package io.github.radumariusi.akvodhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.radumariusi.akvodhub.ui.touch.OutlinedButton

@Composable
internal fun SelectionTab(label: String, active: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier.semantics {
                selected = active
                role = Role.Tab
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, textDecoration = TextDecoration.None)
            val indicator = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
            Box(Modifier.width(32.dp).height(3.dp).background(indicator))
        }
    }
}
