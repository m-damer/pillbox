package com.pillbox.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The weekly summary card on Today. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class WeekSummaryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun card_en() = shoot("card_en", "en") { Card(WeekSummary(18, 20)) }
    @Test fun card_one_en() = shoot("card_one_en", "en") { Card(WeekSummary(1, 1)) }
    @Test fun card_none_taken_en() = shoot("card_zero_en", "en") { Card(WeekSummary(0, 6)) }
    @Test fun card_en_dark() = shoot("card_en_dark", "en", ThemeMode.Dark) { Card(WeekSummary(18, 20)) }
    @Test fun card_en_2x() = shoot("card_en_2x", "en", fontScale = 2f) { Card(WeekSummary(18, 20)) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun card_ar() = shoot("card_ar", "ar") { Card(WeekSummary(18, 20)) }

    @Composable private fun Card(w: WeekSummary) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { WeekSummaryCard(w) }
    }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/weeksummary_$name.png")
    }
}

