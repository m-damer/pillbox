package com.pillbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.pillbox.R
import com.pillbox.domain.DoseUnit
import com.pillbox.ui.theme.PillBox
import java.time.LocalTime
import kotlin.math.floor

/** Plural resource for the word next to a number: "1 tablet", "2 tablets". */
fun unitPlural(t: DoseUnit) = when (t) {
    DoseUnit.TABLET -> R.plurals.unit_tablet
    DoseUnit.CAPSULE -> R.plurals.unit_capsule
    DoseUnit.ML -> R.plurals.unit_ml
    DoseUnit.DROPS -> R.plurals.unit_drops
    DoseUnit.INJECTION -> R.plurals.unit_injection
    DoseUnit.OTHER -> R.plurals.unit_other
}

/** 1 is singular; whole numbers use themselves; halves count as plural. */
fun quantityFor(v: Double): Int = if (v == 1.0) 1 else if (v == floor(v)) v.toInt() else 2

/** The four parts of the day that group doses, with their token colors. */
enum class Block { MORNING, NOON, EVENING, NIGHT }

fun blockOf(t: LocalTime) = when (t.hour) {
    in 5..11 -> Block.MORNING
    in 12..16 -> Block.NOON
    in 17..20 -> Block.EVENING
    else -> Block.NIGHT
}

/** Background and text color of a part of the day. */
@Composable
@ReadOnlyComposable
fun blockColors(b: Block): Pair<Color, Color> {
    val c = PillBox.colors
    return when (b) {
        Block.MORNING -> c.morning to c.morningText
        Block.NOON -> c.noon to c.noonText
        Block.EVENING -> c.evening to c.eveningText
        Block.NIGHT -> c.night to c.nightText
    }
}

@Composable
fun blockName(b: Block, afternoon: Boolean = false) = stringResource(
    when (b) {
        Block.MORNING -> R.string.block_morning
        Block.NOON -> if (afternoon) R.string.block_afternoon else R.string.block_noon
        Block.EVENING -> R.string.block_evening
        Block.NIGHT -> R.string.block_night
    },
)
