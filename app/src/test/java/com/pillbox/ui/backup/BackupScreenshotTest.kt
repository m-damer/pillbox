package com.pillbox.ui.backup

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
import com.pillbox.R
import com.pillbox.domain.BackupData
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.BackupSummary
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Backup and export, Backup failed, Restore (choose, check, done, error) and Delete all done in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class BackupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val folder = BackupSettings(autoFolder = true, folderUri = "content://x", folderLabel = "Documents / Pill Box", lastSuccessAt = LocalDateTime.of(2026, 10, 17, 9, 41))
    private val drive = folder.copy(driveOn = true, driveEmail = "damer@gmail.com", lastToDrive = true)
    private val fresh = BackupSettings()
    private val failing = folder.copy(lastSuccessAt = LocalDateTime.of(2026, 10, 16, 9, 41), lastProblem = BackupProblem.NO_SPACE, lastProblemAt = LocalDateTime.of(2026, 10, 17, 9, 0))
    private val summary = BackupSummary(3, 2, 214)
    private val confirm = RestoreState.Confirm("pillbox-2026-10-12.pillbox", LocalDateTime.of(2026, 10, 12, 9, 41), summary, BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))

    @Composable private fun content(s: BackupSettings, f: Formatter) =
        BackupContent(s, f, today, remember { SnackbarHostState() }, {}, {}, {}, {}, {}, {})

    @Test fun backup_en() = shoot("backup_en", "en") { content(folder, it) }
    @Test fun backup_en_dark_2x() = shoot("backup_en_dark_2x", "en", ThemeMode.Dark, 2f) { content(folder, it) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun backup_ar() = shoot("backup_ar", "ar") { content(folder, it) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun backup_ar_dark_2x() = shoot("backup_ar_dark_2x", "ar", ThemeMode.Dark, 2f) { content(folder, it) }
    @Test fun backup_drive_en() = shoot("backup_drive_en", "en") { content(drive, it) }
    @Test fun backup_drive_en_dark_2x() = shoot("backup_drive_en_dark_2x", "en", ThemeMode.Dark, 2f) { content(drive, it) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun backup_drive_ar() = shoot("backup_drive_ar", "ar") { content(drive, it) }
    @Test fun restore_intro_drive_en() = shoot("restore_intro_drive_en", "en") { RestoreIntroContent({}, {}, onDrive = {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_intro_drive_ar() = shoot("restore_intro_drive_ar", "ar") { RestoreIntroContent({}, {}, onDrive = {}) }
    @Test fun backup_new_en() = shoot("backup_new_en", "en") { content(fresh, it) }

    @Test fun failed_en() = shoot("failed_en", "en") { BackupFailedContent(failing, it, today, false, {}, {}, {}) }
    @Test fun failed_en_dark_2x() = shoot("failed_en_dark_2x", "en", ThemeMode.Dark, 2f) { BackupFailedContent(failing, it, today, false, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun failed_ar() = shoot("failed_ar", "ar") { BackupFailedContent(failing, it, today, false, {}, {}, {}) }

    @Test fun restore_intro_en() = shoot("restore_intro_en", "en") { RestoreIntroContent({}, {}) }
    @Test fun restore_intro_en_dark_2x() = shoot("restore_intro_en_dark_2x", "en", ThemeMode.Dark, 2f) { RestoreIntroContent({}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_intro_ar() = shoot("restore_intro_ar", "ar") { RestoreIntroContent({}, {}) }

    @Test fun restore_confirm_en() = shoot("restore_confirm_en", "en") { RestoreConfirmContent(confirm, it, today, {}, {}, {}) }
    @Test fun restore_confirm_en_dark_2x() = shoot("restore_confirm_en_dark_2x", "en", ThemeMode.Dark, 2f) { RestoreConfirmContent(confirm, it, today, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_confirm_ar() = shoot("restore_confirm_ar", "ar") { RestoreConfirmContent(confirm, it, today, {}, {}, {}) }

    @Test fun restore_done_en() = shoot("restore_done_en", "en") { RestoreDoneContent(summary, it, {}, {}) }
    @Test fun restore_done_en_dark_2x() = shoot("restore_done_en_dark_2x", "en", ThemeMode.Dark, 2f) { RestoreDoneContent(summary, it, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_done_ar() = shoot("restore_done_ar", "ar") { RestoreDoneContent(summary, it, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_done_ar_few() = shoot("restore_done_ar_few", "ar") { RestoreDoneContent(BackupSummary(2, 1, 2), it, {}, {}) }

    @Test fun restore_error_en() = shoot("restore_error_en", "en") { RestoreErrorContent({}, {}, R.string.rs_err_title, R.string.rs_err_body, R.string.rs_err_safe) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun restore_error_ar() = shoot("restore_error_ar", "ar") { RestoreErrorContent({}, {}, R.string.rs_err_title, R.string.rs_err_body, R.string.rs_err_safe) }

    @Test fun delete_done_en() = shoot("delete_done_en", "en") { DeleteDoneContent({}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun delete_done_ar() = shoot("delete_done_ar", "ar") { DeleteDoneContent({}, {}) }

    private fun fmt(ar: Boolean) = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable (Formatter) -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content(fmt(lang == "ar")) }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/backup_$name.png")
    }
}
