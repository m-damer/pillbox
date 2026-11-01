package com.pillbox.domain

import kotlin.math.floor

/** How an amount like 0.5, 1, 2, 11 or 2.5 is written next to a medicine unit. Pure rules; the words live in resources. */
object AmountRules {
    enum class Shape { PLURAL, HALF, ONE_AND_HALF }

    /**
     * Number for the plural rules of the language. Whole numbers use themselves, so Arabic gets one, two, few (3 to 10),
     * many (11 to 99) and other (100 and more). Fractions use 100 so they fall in "other" (Arabic: singular after the number).
     */
    fun quantity(v: Double): Int = if (v == floor(v)) v.toInt() else 100

    /**
     * [wordsForHalves] is a language choice (Arabic says "نصف قرص" and "قرص ونصف"); [supportsHalf] is true for units
     * that are counted in pieces. Everything else uses the plural rules above.
     */
    fun shape(v: Double, wordsForHalves: Boolean, supportsHalf: Boolean): Shape {
        if (!wordsForHalves || !supportsHalf) return Shape.PLURAL
        return when (v) {
            0.5 -> Shape.HALF
            1.5 -> Shape.ONE_AND_HALF
            else -> Shape.PLURAL
        }
    }

    /** Units counted in pieces: halves can be said in words. */
    fun supportsHalf(unit: DoseUnit): Boolean = when (unit) {
        DoseUnit.TABLET, DoseUnit.CAPSULE, DoseUnit.POWDER, DoseUnit.SUPPOSITORY, DoseUnit.PATCH -> true
        else -> false
    }
}
