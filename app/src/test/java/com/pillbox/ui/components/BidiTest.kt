package com.pillbox.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BidiTest {
    @Test fun latinNameIsLeftToRight() = assertEquals("⁦Metformin⁩", isolate("Metformin"))
    @Test fun arabicAmountIsRightToLeft() = assertEquals("⁧٨ أقراص⁩", isolate("٨ أقراص"))
    @Test fun mixedTextFallsBackToFirstStrong() = assertEquals("⁨Vit أ⁩", isolate("Vit أ"))
    @Test fun emptyStaysEmpty() = assertEquals("", isolate(""))
}
