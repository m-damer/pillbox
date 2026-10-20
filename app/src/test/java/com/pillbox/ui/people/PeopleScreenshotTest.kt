package com.pillbox.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DaySummary
import com.pillbox.domain.Person
import com.pillbox.domain.Relation
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

/** People list, Add person, Edit person, Edit my profile and the remove prompt in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class PeopleScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 5, 1)
    private val me = Person(1, "Ali Hassan", isMe = true)
    private val mom = Person(2, "Mom", relation = Relation.PARENT, colorIndex = 0)
    private val dad = Person(3, "Dad", relation = Relation.PARENT, colorIndex = 1)
    private val full = PeopleUiState(
        loaded = true, activeId = 1,
        rows = listOf(
            PersonRow(me, DaySummary(1, 5, 0, null)),
            PersonRow(mom, DaySummary(2, 2, 0, null)),
            PersonRow(dad, DaySummary(0, 2, 1, day.atTime(8, 0))),
        ),
    )
    private val alone = PeopleUiState(loaded = true, activeId = 1, rows = listOf(PersonRow(me, DaySummary(1, 5, 0, null))))
    private val add = PersonEditState(loaded = true, mode = PersonMode.ADD)
    private val edit = PersonEditState(loaded = true, mode = PersonMode.EDIT, name = "Dad", relation = Relation.PARENT, colorIndex = 1)
    private val profile = PersonEditState(loaded = true, mode = PersonMode.PROFILE, isMe = true, name = "Ali Hassan", birthYear = "1958")

    @Test fun list_en() = shoot("list_en", "en") { list(full) }
    @Test fun list_en_dark_2x() = shoot("list_en_dark_2x", "en", ThemeMode.Dark, 2f) { list(full) }
    @Test fun list_empty_en() = shoot("list_empty_en", "en") { list(alone) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun list_ar() = shoot("list_ar", "ar") { list(full) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun list_ar_dark_2x() = shoot("list_ar_dark_2x", "ar", ThemeMode.Dark, 2f) { list(full) }

    @Test fun add_en() = shoot("add_en", "en") { editor(add) }
    @Test fun add_typed_en() = shoot("add_typed_en", "en") { editor(add.copy(name = "Mom", colorIndex = 2)) }
    @Test fun edit_en() = shoot("edit_en", "en") { editor(edit) }
    @Test fun edit_en_dark_2x() = shoot("edit_en_dark_2x", "en", ThemeMode.Dark, 2f) { editor(edit) }
    @Test fun remove_en() = shoot("remove_en", "en") { editor(edit.copy(showRemove = true)) }
    @Test fun profile_en() = shoot("profile_en", "en") { editor(profile) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun add_ar() = shoot("add_ar", "ar") { editor(add) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun edit_ar() = shoot("edit_ar", "ar") { editor(edit.copy(name = "أبي")) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun edit_ar_dark_2x() = shoot("edit_ar_dark_2x", "ar", ThemeMode.Dark, 2f) { editor(edit.copy(name = "أبي")) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun profile_ar() = shoot("profile_ar", "ar") { editor(profile.copy(name = "علي حسن")) }

    @androidx.compose.runtime.Composable
    private fun list(s: PeopleUiState) = PeopleContent(s, fmt(false), {}, {}, {}, {})

    @androidx.compose.runtime.Composable
    private fun editor(s: PersonEditState) = PersonEditContent(s, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun fmt(ar: Boolean) = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/people_$name.png")
    }
}
