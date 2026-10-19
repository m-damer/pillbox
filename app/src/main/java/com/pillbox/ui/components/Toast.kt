package com.pillbox.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** Shows a two-line toast (bold title, then a sentence). It is announced to TalkBack as a status message. */
suspend fun SnackbarHostState.showToast(title: String, body: String? = null) {
    showSnackbar(if (body == null) title else "$title\n$body", duration = SnackbarDuration.Short)
}

/** Host for [showToast]. Put it at the bottom of a screen, above the buttons. */
@Composable
fun ToastHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) { data ->
        val c = PillBox.colors
        val parts = data.visuals.message.split("\n", limit = 2)
        Snackbar(containerColor = c.text, contentColor = c.background, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(parts[0], style = MaterialTheme.typography.labelLarge)
                if (parts.size > 1) Text(parts[1], style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
