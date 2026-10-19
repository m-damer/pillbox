package com.pillbox.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Every text color must reach 4.5:1 on the background it is drawn on, in all three palettes. */
class ContrastTest {

    private val palettes = mapOf("light" to LightColors, "dark" to DarkColors, "highContrast" to HighContrastColors)

    private val pairs: List<Pair<String, (PillBoxColors) -> Pair<Color, Color>>> = listOf(
        "text on background" to { it.text to it.background },
        "text on card" to { it.text to it.card },
        "textMuted on background" to { it.textMuted to it.background },
        "textMuted on card" to { it.textMuted to it.card },
        "placeholder on card" to { it.placeholder to it.card },
        "onPrimary on primary" to { it.onPrimary to it.primary },
        "accent on card" to { it.accent to it.card },
        "accent on background" to { it.accent to it.background },
        "text on primaryContainer" to { it.text to it.primaryContainer },
        "accent on primaryContainer" to { it.accent to it.primaryContainer },
        "success on successContainer" to { it.success to it.successContainer },
        "warning on warningContainer" to { it.warning to it.warningContainer },
        "danger on dangerContainer" to { it.danger to it.dangerContainer },
        "danger on card" to { it.danger to it.card },
        "morningText on morning" to { it.morningText to it.morning },
        "noonText on noon" to { it.noonText to it.noon },
        "eveningText on evening" to { it.eveningText to it.evening },
        "nightText on night" to { it.nightText to it.night },
    )

    @Test
    fun allTextPairsMeetMinimumContrast() {
        val failures = palettes.flatMap { (name, colors) ->
            pairs.mapNotNull { (label, pick) ->
                val (fg, bg) = pick(colors)
                val ratio = contrast(fg, bg)
                if (ratio < 4.5) "$name: $label is ${"%.2f".format(ratio)}:1" else null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
