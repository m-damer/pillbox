package com.pillbox.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.EmailCheck
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Privacy and about, Privacy policy, Terms, Send feedback (ok, missing, invalid), Rate the app in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class AboutScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun about() = AboutContent("1.0", remember { SnackbarHostState() }, {}, {}, {}, {}, {})
    @Composable private fun feedback(check: EmailCheck, email: String = "") =
        FeedbackContent(0, {}, "", {}, email, {}, "", {}, true, {}, check, {}, {})

    @Test fun about_en() = shoot("about_en", "en") { about() }
    @Test fun about_en_dark_2x() = shoot("about_en_dark_2x", "en", ThemeMode.Dark, 2f) { about() }
    @Config(qualifiers = "+ar-ldrtl") @Test fun about_ar() = shoot("about_ar", "ar") { about() }

    @Test fun privacy_en() = shoot("privacy_en", "en") { PrivacyPolicyContent({}, {}, {}) }
    @Test fun privacy_en_dark_2x() = shoot("privacy_en_dark_2x", "en", ThemeMode.Dark, 2f) { PrivacyPolicyContent({}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun privacy_ar() = shoot("privacy_ar", "ar") { PrivacyPolicyContent({}, {}, {}) }

    @Test fun terms_en() = shoot("terms_en", "en") { TermsContent({}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun terms_ar() = shoot("terms_ar", "ar") { TermsContent({}, {}) }

    @Test fun feedback_en() = shoot("feedback_en", "en") { feedback(EmailCheck.OK) }
    @Test fun feedback_en_dark_2x() = shoot("feedback_en_dark_2x", "en", ThemeMode.Dark, 2f) { feedback(EmailCheck.OK) }
    @Test fun feedback_missing_en() = shoot("feedback_missing_en", "en") { feedback(EmailCheck.EMPTY) }
    @Test fun feedback_invalid_en() = shoot("feedback_invalid_en", "en") { feedback(EmailCheck.INVALID, "name@") }
    @Config(qualifiers = "+ar-ldrtl") @Test fun feedback_ar() = shoot("feedback_ar", "ar") { feedback(EmailCheck.OK) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun feedback_invalid_ar() = shoot("feedback_invalid_ar", "ar") { feedback(EmailCheck.INVALID, "name@") }

    @Test fun developer_en() = shoot("developer_en", "en") { DeveloperContent({}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun developer_ar() = shoot("developer_ar", "ar") { DeveloperContent({}) }

    @Test fun rate_en() = shoot("rate_en", "en") { RateContent(4, {}, {}, {}, {}, {}) }
    @Test fun rate_none_en() = shoot("rate_none_en", "en") { RateContent(0, {}, {}, {}, {}, {}) }
    @Test fun rate_en_dark_2x() = shoot("rate_en_dark_2x", "en", ThemeMode.Dark, 2f) { RateContent(4, {}, {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun rate_ar() = shoot("rate_ar", "ar") { RateContent(4, {}, {}, {}, {}, {}) }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/about_$name.png")
    }
}
