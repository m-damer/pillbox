package com.pillbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pillbox.R
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** Stands in for People and Settings until M7 and M8. */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.ScreenPaddingH)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = Space.S12).semantics { heading() })
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge, color = c.textMuted, modifier = Modifier.padding(top = Space.S8))
    }
}
