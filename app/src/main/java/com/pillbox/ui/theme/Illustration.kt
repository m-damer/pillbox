package com.pillbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colors the design canvas uses for drawings (medicine shapes, the day bar, the stock bar) that are
 * not in pillbox-tokens.json yet. Kept in one place so they can move into the token file.
 */
data class Illustration(
    val tablet: Color,
    val capsule: Color,
    val paused: Color,
    val stockLow: Color,
    /** Day bar gradient from midnight to midnight: position (0..1) to color. */
    val dayBar: List<Pair<Float, Color>>,
    /** Avatar backgrounds the user can pick for a person (PeopleRules.COLORS of them). */
    val avatars: List<Color>,
    val legendNight: Color, val legendSunrise: Color, val legendDay: Color, val legendSunset: Color,
)

private val Sunrise = Color(0xFFFFC970)
private val DaySky = Color(0xFF9ED3F5)
private val SunsetOrange = Color(0xFFFFB36B)
private val SunsetPurple = Color(0xFF7A5FD0)

private fun bar(night: Color, dusk: Color) = listOf(
    0f to night, 0.17f to dusk, 0.28f to Sunrise, 0.38f to DaySky, 0.66f to DaySky,
    0.76f to SunsetOrange, 0.85f to SunsetPurple, 0.92f to dusk, 1f to night,
)

private val LightIllustration = Illustration(
    tablet = Color(0xFF2F6FED), capsule = Color(0xFFE4572E), paused = Color(0xFF8A90A8), stockLow = Color(0xFFD98200),
    dayBar = bar(Color(0xFF26304D), Color(0xFF3B4A7A)),
    avatars = listOf(0xFFDDE1F5, 0xFFE3F4E8, 0xFFFFE4E0, 0xFFFFF1D6, 0xFFF0E8FF, 0xFFDDF0FF).map { Color(it) },
    legendNight = Color(0xFF26304D), legendSunrise = Sunrise, legendDay = DaySky, legendSunset = SunsetPurple,
)

private val DarkIllustration = LightIllustration.copy(
    paused = Color(0xFFAEB5D3),
    avatars = listOf(0xFF2B3158, 0xFF1F3B2C, 0xFF4A2A2A, 0xFF4A3E22, 0xFF3A2E5A, 0xFF1F3A4F).map { Color(it) },
    dayBar = bar(Color(0xFFC5CCEE), Color(0xFFB8C2F0)),
    legendNight = Color(0xFFC5CCEE),
)

val PillBox.illustration: Illustration
    @Composable @ReadOnlyComposable get() = if (colors.background.luminance() < 0.5f) DarkIllustration else LightIllustration
