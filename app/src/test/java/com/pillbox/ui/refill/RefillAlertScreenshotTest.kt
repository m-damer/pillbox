package com.pillbox.ui.refill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.RefillSettings
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Refill alert screen in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class RefillAlertScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val state = RefillUiState(
        loaded = true, settings = RefillSettings(daysBefore = 5), original = RefillSettings(daysBefore = 5),
        preview = PreviewMedicine("Metformin", DoseUnit.TABLET, 8.0, 4),
    )
    private val today = LocalDate.now()

    @Test fun en() = shoot("en", "en")
    @Test fun en_dark_2x() = shoot("en_dark_2x", "en", ThemeMode.Dark, 2f)
    @Test fun en_custom_days() = shoot("en_custom_days", "en", s = state.copy(settings = state.settings.copy(daysBefore = 12), original = state.original))
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar() = shoot("ar", "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x() = shoot("ar_dark_2x", "ar", ThemeMode.Dark, 2f)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, s: RefillUiState = state) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        RefillAlertContent(s, fmt, today, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/refill_$name.png")
    }
}
