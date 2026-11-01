package com.pillbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountRulesTest {
    @Test fun wholeNumbersUseThemselves() {
        assertEquals(1, AmountRules.quantity(1.0)); assertEquals(2, AmountRules.quantity(2.0))
        assertEquals(10, AmountRules.quantity(10.0)); assertEquals(11, AmountRules.quantity(11.0)); assertEquals(100, AmountRules.quantity(100.0))
    }

    @Test fun fractionsFallInOther() {
        assertEquals(100, AmountRules.quantity(0.5)); assertEquals(100, AmountRules.quantity(2.5))
    }

    @Test fun halvesInWordsOnlyWhenAllowed() {
        assertEquals(AmountRules.Shape.HALF, AmountRules.shape(0.5, true, true))
        assertEquals(AmountRules.Shape.ONE_AND_HALF, AmountRules.shape(1.5, true, true))
        assertEquals(AmountRules.Shape.PLURAL, AmountRules.shape(2.5, true, true))
        assertEquals(AmountRules.Shape.PLURAL, AmountRules.shape(0.5, false, true))
        assertEquals(AmountRules.Shape.PLURAL, AmountRules.shape(0.5, true, false))
    }

    @Test fun pieceUnitsSupportHalves() {
        assertTrue(AmountRules.supportsHalf(DoseUnit.TABLET))
        assertFalse(AmountRules.supportsHalf(DoseUnit.ML))
        assertFalse(AmountRules.supportsHalf(DoseUnit.DROPS))
    }
}
