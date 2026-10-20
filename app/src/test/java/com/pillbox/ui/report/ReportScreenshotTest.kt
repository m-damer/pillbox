package com.pillbox.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Doctor report options screen in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class ReportScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val ready = ReportUiState(loaded = true, patient = "Me", pages = 1)
    private val making = ReportUiState(loaded = true, patient = "Me", pages = null)

    @Test fun en() = shoot("en", "en", ready)
    @Test fun en_making() = shoot("en_making", "en", making)
    @Test fun en_dark_2x() = shoot("en_dark_2x", "en", ready, ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar() = shoot("ar", "ar", ready.copy(patient = "أنا"))
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x() = shoot("ar_dark_2x", "ar", ready.copy(patient = "أنا"), ThemeMode.Dark, 2f)

    private fun shoot(name: String, lang: String, s: ReportUiState, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ReportContent(s, fmt, remember { SnackbarHostState() }, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/report_$name.png")
    }
}
