package com.pillbox.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.pillbox.domain.Person
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The person pill, the short "viewing" notice and the popup list, en/ar, light/dark, font scale 1.0/2.0, with 2 and 12 people. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class SwitcherScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val names = listOf("Me", "Abdullah", "Fatima Al-Hassan", "عبد الله", "Mom", "Dad", "Grandma Layla", "Grandpa", "Sara", "Omar", "Huda", "Yusuf")
    private fun people(n: Int) = names.take(n).mapIndexed { i, nm -> Person(id = i + 1L, name = nm, isMe = i == 0, colorIndex = i, ring = i != 2) }
    private val unread = mapOf(1L to 1, 2L to 3, 3L to 12, 5L to 120)
    private fun bar(n: Int, active: Long, withUnread: Boolean = true) = PeopleBar(people(n), active, {}, if (withUnread) unread else emptyMap())

    @Test fun header_no_badge_en() = shoot("header_no_badge_en", "en") { Header(bar(3, 2, withUnread = false)) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun header_badge_ar() = shoot("header_badge_ar", "ar") { Header(bar(3, 1)) }
    @Test fun header_me_en() = shoot("header_me_en", "en") { Header(bar(2, 1)) }
    @Test fun header_other_en() = shoot("header_other_en", "en") { Header(bar(2, 2)) }
    @Test fun header_arabic_name_in_en() = shoot("header_arabic_name_en", "en") { Header(bar(4, 4)) }
    @Test fun header_long_name_en() = shoot("header_long_name_en", "en") { Header(bar(3, 3)) }
    @Test fun header_long_name_en_2x() = shoot("header_long_name_en_2x", "en", fontScale = 2f) { Header(bar(3, 3)) }
    @Test fun header_other_en_dark() = shoot("header_other_en_dark", "en", ThemeMode.Dark) { Header(bar(2, 2)) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun header_other_ar() = shoot("header_other_ar", "ar") { Header(bar(2, 2)) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun header_english_name_in_ar() = shoot("header_english_name_ar", "ar") { Header(bar(4, 4)) }
    @Test fun popup_3_en() = shoot("popup_3_en", "en") { Popup(3, 2) }
    @Test fun popup_12_en() = shoot("popup_12_en", "en") { Popup(12, 7) }
    @Test fun popup_12_en_2x() = shoot("popup_12_en_2x", "en", fontScale = 2f) { Popup(12, 7) }
    @Test fun popup_12_en_dark() = shoot("popup_12_en_dark", "en", ThemeMode.Dark) { Popup(12, 1) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun popup_12_ar() = shoot("popup_12_ar", "ar") { Popup(12, 4) }

    @Composable private fun Header(b: PeopleBar) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { Text("Today", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground) }
                PeopleBarSwitcher(b)
            }
            ViewingBanner(b)
        }
    }

    @Composable private fun Popup(n: Int, active: Long) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            PersonPickerCard(rememberChips(people(n)), active, unread, {}, {})
        }
    }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/switcher_$name.png")
    }
}
