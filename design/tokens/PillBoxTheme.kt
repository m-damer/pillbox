package com.pillbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Generated from pillbox-tokens.json (design canvas v96). Change the JSON first, then this file.

@Immutable
data class PillBoxColors(
    val background: Color, val card: Color, val text: Color, val textMuted: Color,
    val primary: Color, val onPrimary: Color, val accent: Color, val primaryContainer: Color,
    val divider: Color, val border: Color, val placeholder: Color,
    val success: Color, val successContainer: Color,
    val warning: Color, val warningContainer: Color,
    val danger: Color, val dangerContainer: Color,
    val morning: Color, val morningText: Color, val noon: Color, val noonText: Color,
    val evening: Color, val eveningText: Color, val night: Color, val nightText: Color,
    val cardBorderWidth: androidx.compose.ui.unit.Dp,
)

private fun c(hex: Long) = Color(hex)

val LightColors = PillBoxColors(
    background = c(0xFFF3F5FA),
    card = c(0xFFFFFFFF),
    text = c(0xFF171B2E),
    textMuted = c(0xFF596079),
    primary = c(0xFF4338CA),
    onPrimary = c(0xFFFFFFFF),
    accent = c(0xFF4338CA),
    primaryContainer = c(0xFFEEF0FF),
    divider = c(0xFFE4E8F4),
    border = c(0xFFC9CDE6),
    placeholder = c(0xFF667085),
    success = c(0xFF1B5E36),
    successContainer = c(0xFFE3F4E8),
    warning = c(0xFF6B4200),
    warningContainer = c(0xFFFFF1D6),
    danger = c(0xFFB42318),
    dangerContainer = c(0xFFFFE4E0),
    morning = c(0xFFE3F4E8),
    morningText = c(0xFF1B5E36),
    noon = c(0xFFDDF0FF),
    noonText = c(0xFF0B4A73),
    evening = c(0xFFF0E8FF),
    eveningText = c(0xFF45308F),
    night = c(0xFFE4E8F4),
    nightText = c(0xFF26304D),
    cardBorderWidth = 0.dp,
)

val DarkColors = PillBoxColors(
    background = c(0xFF0F1220),
    card = c(0xFF1B2033),
    text = c(0xFFEDEFFA),
    textMuted = c(0xFFAEB5D3),
    primary = c(0xFF4338CA),
    onPrimary = c(0xFFFFFFFF),
    accent = c(0xFFB4AFFF),
    primaryContainer = c(0xFF262C4D),
    divider = c(0xFF2C3350),
    border = c(0xFF3A4166),
    placeholder = c(0xFFA7AECB),
    success = c(0xFF8FE0AB),
    successContainer = c(0xFF17382A),
    warning = c(0xFFFFD28A),
    warningContainer = c(0xFF3D2D0A),
    danger = c(0xFFFF8A7A),
    dangerContainer = c(0xFF4A1F1B),
    morning = c(0xFF17382A),
    morningText = c(0xFF8FE0AB),
    noon = c(0xFF12344D),
    noonText = c(0xFF8FD0FF),
    evening = c(0xFF2B2250),
    eveningText = c(0xFFCDB9FF),
    night = c(0xFF232A45),
    nightText = c(0xFFC5CCEE),
    cardBorderWidth = 0.dp,
)

val HighContrastColors = PillBoxColors(
    background = c(0xFFFFFFFF),
    card = c(0xFFFFFFFF),
    text = c(0xFF171B2E),
    textMuted = c(0xFF171B2E),
    primary = c(0xFF1B1491),
    onPrimary = c(0xFFFFFFFF),
    accent = c(0xFF1B1491),
    primaryContainer = c(0xFFEEF0FF),
    divider = c(0xFFE4E8F4),
    border = c(0xFF171B2E),
    placeholder = c(0xFF171B2E),
    success = c(0xFF1B5E36),
    successContainer = c(0xFFE3F4E8),
    warning = c(0xFF6B4200),
    warningContainer = c(0xFFFFF1D6),
    danger = c(0xFFB42318),
    dangerContainer = c(0xFFFFE4E0),
    morning = c(0xFFFFFFFF),
    morningText = c(0xFF1B5E36),
    noon = c(0xFFFFFFFF),
    noonText = c(0xFF0B4A73),
    evening = c(0xFFFFFFFF),
    eveningText = c(0xFF45308F),
    night = c(0xFFFFFFFF),
    nightText = c(0xFF26304D),
    cardBorderWidth = 2.dp,
)

val LocalPillBoxColors = staticCompositionLocalOf { LightColors }

enum class ThemeMode { System, Light, Dark }

@Composable
fun PillBoxTheme(mode: ThemeMode = ThemeMode.System, highContrast: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val pb = if (highContrast) HighContrastColors else if (dark) DarkColors else LightColors
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = pb.primary, onPrimary = pb.onPrimary, background = pb.background, onBackground = pb.text,
        surface = pb.card, onSurface = pb.text, onSurfaceVariant = pb.textMuted,
        outline = pb.border, outlineVariant = pb.divider, error = pb.danger, errorContainer = pb.dangerContainer,
    )
    CompositionLocalProvider(LocalPillBoxColors provides pb) {
        MaterialTheme(colorScheme = scheme, typography = PillBoxTypography, shapes = PillBoxShapes, content = content)
    }
}

// Fonts: add Inter and Baloo Bhaijaan 2 as downloadable or bundled fonts, then pick by locale.
// val Inter = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_semibold, FontWeight.SemiBold))
// val Baloo = FontFamily(Font(R.font.baloo_bhaijaan_2_regular), Font(R.font.baloo_bhaijaan_2_semibold, FontWeight.SemiBold))
val AppFont: FontFamily = FontFamily.Default

private fun t(size: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, fontWeight = w, lineHeight = (size * 1.4f).sp)

val PillBoxTypography = Typography(
    labelSmall = t(13), bodySmall = t(14), bodyMedium = t(15), bodyLarge = t(16),
    titleMedium = t(20, FontWeight.SemiBold), titleLarge = t(22, FontWeight.SemiBold),
    headlineSmall = t(24, FontWeight.SemiBold), displaySmall = t(36, FontWeight.SemiBold),
    displayMedium = t(40, FontWeight.SemiBold), displayLarge = t(48, FontWeight.SemiBold),
    labelLarge = t(16, FontWeight.SemiBold), labelMedium = t(15, FontWeight.SemiBold),
)

val PillBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

object Dimens {
    val ScreenPaddingH = 16.dp; val ScreenPaddingTop = 12.dp; val ScreenPaddingBottom = 20.dp
    val CardGap = 12.dp; val ListRowMinHeight = 56.dp; val SettingsRowMinHeight = 64.dp
    val TouchTargetMin = 48.dp; val PrimaryButtonHeight = 52.dp; val BottomNavHeight = 80.dp; val Fab = 60.dp
    val SwitchWidth = 52.dp; val SwitchHeight = 32.dp
    val ButtonShape = RoundedCornerShape(percent = 50)
}
