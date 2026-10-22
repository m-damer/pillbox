package com.pillbox.ui.addmed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseUnit
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import com.pillbox.ui.today.PillIcon
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The type popup and the type button, with every icon at 36 dp and 24 dp, en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class TypePickerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun popup_en() = shoot("popup_en", "en") { Popup(DoseUnit.TABLET) }
    @Test fun popup_en_cream() = shoot("popup_en_cream", "en") { Popup(DoseUnit.CREAM) }
    @Test fun popup_en_dark() = shoot("popup_en_dark", "en", ThemeMode.Dark) { Popup(DoseUnit.INHALER) }
    @Test fun popup_en_light_2x() = shoot("popup_en_light_2x", "en", ThemeMode.Light, 2f) { Popup(DoseUnit.TABLET) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun popup_ar() = shoot("popup_ar", "ar") { Popup(DoseUnit.SPRAY) }
    @Test fun button_en() = shoot("button_en", "en") { Button(DoseUnit.GEL) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun button_ar() = shoot("button_ar", "ar") { Button(DoseUnit.PATCH) }
    @Test fun icons_light() = shoot("icons_light", "en") { Icons() }
    @Test fun icons_dark() = shoot("icons_dark", "en", ThemeMode.Dark) { Icons() }

    @androidx.compose.runtime.Composable private fun Popup(sel: DoseUnit) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            TypePickerCard(sel, {}, {}, {})
        }
    }

    @androidx.compose.runtime.Composable private fun Button(sel: DoseUnit) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { TypeButton(sel, {}) }
    }

    @androidx.compose.runtime.Composable private fun Icons() {
        Column(Modifier.fillMaxSize().background(PillBox.colors.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DoseUnit.entries.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { PillIcon(it, size = 56.dp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { DoseUnit.entries.take(8).forEach { PillIcon(it, size = 24.dp) } }
            Text("24 dp", style = MaterialTheme.typography.bodySmall, color = PillBox.colors.textMuted)
        }
    }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/type_$name.png")
    }
}
