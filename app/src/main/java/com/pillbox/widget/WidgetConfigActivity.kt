package com.pillbox.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.PillBoxActivity
import com.pillbox.PillBoxApp
import com.pillbox.R
import com.pillbox.domain.Person
import com.pillbox.domain.WidgetSettings
import com.pillbox.domain.WidgetSize
import com.pillbox.ui.DisplayState
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.settings.Caption
import com.pillbox.ui.settings.Card
import com.pillbox.ui.settings.Note
import com.pillbox.ui.settings.SubHeader
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.PillBoxAppTheme
import com.pillbox.ui.theme.Space
import kotlinx.coroutines.launch

/** "Widget settings": opens when a widget is added (and from the widget's reconfigure option). Saves what the widget shows. */
class WidgetConfigActivity : PillBoxActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the person leaves without saving, Android does not add the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val app = application as PillBoxApp
        setContent {
            val display by app.displaySettings.flow.collectAsStateWithLifecycle(DisplayState.current)
            PillBoxAppTheme(display) {
                val scope = rememberCoroutineScope()
                val people by app.repository.observePeople().collectAsState(emptyList())
                var settings by remember { mutableStateOf<WidgetSettings?>(null) }
                LaunchedEffect(Unit) { settings = app.widgetSettings.current() }
                val s = settings
                if (s != null) {
                    WidgetSettingsContent(
                        s, people, onChange = { settings = it }, onBack = { finish() },
                        onSave = {
                            scope.launch {
                                app.widgetSettings.save(s)
                                PillBoxWidget.refresh(applicationContext)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                Toast.makeText(applicationContext, getString(R.string.w_saved_title) + "\n" + getString(R.string.w_saved_body), Toast.LENGTH_LONG).show()
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun WidgetSettingsContent(s: WidgetSettings, people: List<Person>, onChange: (WidgetSettings) -> Unit, onBack: () -> Unit, onSave: () -> Unit) {
    val c = PillBox.colors
    val chosen = s.personId ?: people.firstOrNull { it.isMe }?.id
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        SubHeader(stringResource(R.string.w_cfg_title), onBack)
        Text(stringResource(R.string.w_cfg_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = Dimens.ScreenPaddingH + Space.S8))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            Caption(stringResource(R.string.w_cfg_size))
            Segmented(
                listOf(stringResource(R.string.w_small), stringResource(R.string.w_medium), stringResource(R.string.w_large)),
                WidgetSize.entries.indexOf(s.size), { onChange(s.copy(size = WidgetSize.entries[it])) }, stackWhenLarge = true,
            )
            if (people.size > 1) {
                Caption(stringResource(R.string.w_cfg_person))
                ChipGroup {
                    people.forEach { p ->
                        PBChip(if (p.isMe) stringResource(R.string.person_me) else p.name, p.id == chosen, { onChange(s.copy(personId = if (p.isMe) null else p.id)) })
                    }
                }
            }
            Card {
                SwitchRow(stringResource(R.string.w_cfg_names), s.showNames, { onChange(s.copy(showNames = it)) }, subtitle = stringResource(R.string.w_cfg_names_sub))
                SwitchRow(stringResource(R.string.w_cfg_take), s.takeAll, { onChange(s.copy(takeAll = it)) }, subtitle = stringResource(R.string.w_cfg_take_sub))
            }
            Note(stringResource(R.string.w_cfg_note))
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
            PrimaryButton(stringResource(R.string.w_cfg_save), onSave)
        }
    }
}
