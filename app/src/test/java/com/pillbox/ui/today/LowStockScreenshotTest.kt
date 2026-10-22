package com.pillbox.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.pillbox.domain.StockLevel
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The low-stock strip above the week days and its popup list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class LowStockScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun items(n: Int) = listOf(
        LowStock("Metformin", DoseUnit.TABLET, 4, StockLevel.BUY_SOON), LowStock("Vitamin D", DoseUnit.ML, null, StockLevel.OUT),
        LowStock("Salbutamol", DoseUnit.INHALER, 2, StockLevel.BUY_SOON), LowStock("Hydrocortisone", DoseUnit.CREAM, 6, StockLevel.BUY_SOON),
        LowStock("Aspirin", DoseUnit.TABLET, 1, StockLevel.BUY_SOON), LowStock("Insulin", DoseUnit.INJECTION, 3, StockLevel.BUY_SOON),
    ).take(n)

    @Test fun bar_1_en() = shoot("bar_1_en", "en") { Bar(1) }
    @Test fun bar_3_en() = shoot("bar_3_en", "en") { Bar(3) }
    @Test fun bar_3_en_2x() = shoot("bar_3_en_2x", "en", fontScale = 2f) { Bar(3) }
    @Test fun bar_3_en_dark() = shoot("bar_3_en_dark", "en", ThemeMode.Dark) { Bar(3) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun bar_2_ar() = shoot("bar_2_ar", "ar") { Bar(2) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun bar_3_ar() = shoot("bar_3_ar", "ar") { Bar(3) }
    @Test fun popup_3_en() = shoot("popup_3_en", "en") { Popup(3) }
    @Test fun popup_6_en_2x() = shoot("popup_6_en_2x", "en", fontScale = 2f) { Popup(6) }
    @Test fun popup_3_en_dark() = shoot("popup_3_en_dark", "en", ThemeMode.Dark) { Popup(3) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun popup_3_ar() = shoot("popup_3_ar", "ar") { Popup(3) }

    @Composable private fun Bar(n: Int) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { LowStockBar(n) {} }
    }

    @Composable private fun Popup(n: Int) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            LowStockCard(items(n), {}, {})
        }
    }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lowstock_$name.png")
    }
}
