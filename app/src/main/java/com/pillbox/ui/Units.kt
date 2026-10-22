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
    DoseUnit.POWDER -> R.plurals.unit_powder
    DoseUnit.SOLUTION -> R.plurals.unit_ml
    DoseUnit.SPRAY -> R.plurals.unit_spray
    DoseUnit.INHALER -> R.plurals.unit_inhaler
    DoseUnit.SUPPOSITORY -> R.plurals.unit_suppository
    DoseUnit.CREAM, DoseUnit.OINTMENT, DoseUnit.GEL -> R.plurals.unit_application
    DoseUnit.PATCH -> R.plurals.unit_patch
}

/** The name shown in the type popup and on the type button. */
fun typeName(t: DoseUnit) = when (t) {
    DoseUnit.TABLET -> R.string.type_tablet
    DoseUnit.CAPSULE -> R.string.type_capsule
    DoseUnit.POWDER -> R.string.type_powder
    DoseUnit.ML -> R.string.type_syrup
    DoseUnit.SOLUTION -> R.string.type_solution
    DoseUnit.DROPS -> R.string.type_drops
    DoseUnit.SPRAY -> R.string.type_spray
    DoseUnit.INHALER -> R.string.type_inhaler
    DoseUnit.SUPPOSITORY -> R.string.type_suppository
    DoseUnit.CREAM -> R.string.type_cream
    DoseUnit.OINTMENT -> R.string.type_ointment
    DoseUnit.GEL -> R.string.type_gel
    DoseUnit.PATCH -> R.string.type_patch
    DoseUnit.INJECTION -> R.string.type_injection
    DoseUnit.OTHER -> R.string.type_other
}

/** "Tablets per box", "Puffs per inhaler": what Add stock asks for. */
fun perPackLabel(t: DoseUnit) = when (t) {
    DoseUnit.TABLET -> R.string.per_tablet
    DoseUnit.CAPSULE -> R.string.per_capsule
    DoseUnit.ML -> R.string.per_ml
    DoseUnit.DROPS -> R.string.per_drops
    DoseUnit.INJECTION -> R.string.per_injection
    DoseUnit.OTHER -> R.string.per_other
    DoseUnit.POWDER -> R.string.per_powder
    DoseUnit.SOLUTION -> R.string.per_ml
    DoseUnit.SPRAY -> R.string.per_spray
    DoseUnit.INHALER -> R.string.per_inhaler
    DoseUnit.SUPPOSITORY -> R.string.per_suppository
    DoseUnit.CREAM, DoseUnit.OINTMENT, DoseUnit.GEL -> R.string.per_tube
    DoseUnit.PATCH -> R.string.per_patch
}

/** "Boxes bought", "Bottles bought", "Tubes bought". */
fun boughtLabel(t: DoseUnit) = when (com.pillbox.domain.MedicineTypes.packKind(t)) {
    com.pillbox.domain.PackKind.BOX -> R.string.stock_boxes
    com.pillbox.domain.PackKind.BOTTLE -> R.string.stock_bottles
    com.pillbox.domain.PackKind.TUBE -> R.string.stock_tubes
    com.pillbox.domain.PackKind.INHALER -> R.string.stock_inhalers
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
