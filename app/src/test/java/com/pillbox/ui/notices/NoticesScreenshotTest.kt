package com.pillbox.ui.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.Notice
import com.pillbox.domain.NoticeKind
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The notifications screen with tabs and the bell with its badge, en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class NoticesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 22, 16, 0)
    private fun ms(h: Int, daysAgo: Long = 0) = now.minusDays(daysAgo).withHour(h).withMinute(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val all = listOf(
        Notice("a", NoticeKind.DOSE, "Time for your Morning medicines", "Metformin 500 mg · Vitamin D", ms(8), false, "today"),
        Notice("b", NoticeKind.STOCK, "Metformin is running low", "12 tablets left", ms(9), false, "medicine:1"),
        Notice("c", NoticeKind.EXPIRY, "Aspirin expires soon", "In 7 days (Oct 2026). Check the box before using it.", ms(9, 1), true, "medicine:2"),
        Notice("d", NoticeKind.APP, "A new version is ready", "Pill Box 1.2.0 can be installed.", ms(10, 3), true, "updates"),
        Notice("e", NoticeKind.DOSE, "Time for your Evening medicines", "Metformin 500 mg", ms(20, 1), true, "today"),
    )

    @Test fun all_en() = shoot("all_en", "en") { NoticesScreen(all, {}, {}, {}, now = now) }
    @Test fun doses_en() = shoot("doses_en", "en") { NoticesScreen(all, {}, {}, {}, initialTab = NoticeKind.DOSE, now = now) }
    @Test fun empty_tab_en() = shoot("empty_en", "en") { NoticesScreen(emptyList(), {}, {}, {}, now = now) }
    @Test fun all_en_dark() = shoot("all_en_dark", "en", ThemeMode.Dark) { NoticesScreen(all, {}, {}, {}, now = now) }
    @Test fun all_en_2x() = shoot("all_en_2x", "en", fontScale = 2f) { NoticesScreen(all, {}, {}, {}, now = now) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun all_ar() = shoot("all_ar", "ar") { NoticesScreen(all, {}, {}, {}, now = now) }
    @Test fun bell() = shoot("bell", "en") { Row(Modifier.padding(16.dp).background(MaterialTheme.colorScheme.background)) { NoticeBell(0, {}); NoticeBell(3, {}); NoticeBell(120, {}) } }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/notices_$name.png")
    }
}
