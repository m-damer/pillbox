package com.pillbox.ui

import android.content.Context
import com.pillbox.domain.DoseUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Arabic counting rules for medicine units: واحد، مثنى، جمع (3 إلى 10)، مفرد بعد 11، ونصف. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class AmountTextTest {
    private val ctx get() = RuntimeEnvironment.getApplication() as Context
    private val res get() = ctx.resources
    private val fmt get() = Formatter.from(ctx)
    private fun t(v: Double, u: DoseUnit = DoseUnit.TABLET) = amountText(res, fmt, v, u)

    @Test fun tablets() {
        assertEquals("نصف قرص", t(0.5))
        assertEquals("قرص واحد", t(1.0))
        assertEquals("قرص ونصف", t(1.5))
        assertEquals("قرصان", t(2.0))
        assertEquals(fmt.amount(3.0) + " أقراص", t(3.0))
        assertEquals(fmt.amount(10.0) + " أقراص", t(10.0))
        assertEquals(fmt.amount(11.0) + " قرص", t(11.0))
        assertEquals(fmt.amount(12.0) + " قرص", t(12.0))
        assertEquals(fmt.amount(99.0) + " قرص", t(99.0))
        assertEquals(fmt.amount(100.0) + " قرص", t(100.0))
        assertEquals(fmt.amount(2.5) + " قرص", t(2.5))
        assertEquals(fmt.amount(0.0) + " قرص", t(0.0))
    }

    @Test fun otherUnits() {
        assertEquals("كبسولة واحدة", t(1.0, DoseUnit.CAPSULE))
        assertEquals("كبسولتان", t(2.0, DoseUnit.CAPSULE))
        assertEquals(fmt.amount(5.0) + " كبسولات", t(5.0, DoseUnit.CAPSULE))
        assertEquals("قطرة واحدة", t(1.0, DoseUnit.DROPS))
        assertEquals("قطرتان", t(2.0, DoseUnit.DROPS))
        assertEquals(fmt.amount(12.0) + " قطرة", t(12.0, DoseUnit.DROPS))
        assertEquals(fmt.amount(2.0) + " مل", t(2.0, DoseUnit.ML))
        assertEquals(fmt.amount(0.5) + " مل", t(0.5, DoseUnit.ML))
        assertEquals("حقنة واحدة", t(1.0, DoseUnit.INJECTION))
        assertEquals(fmt.amount(4.0) + " حقن", t(4.0, DoseUnit.INJECTION))
        assertEquals("لصقة واحدة", t(1.0, DoseUnit.PATCH))
        assertEquals("نصف لصقة", t(0.5, DoseUnit.PATCH))
    }

    @Test fun pluralWordAlone() {
        assertEquals("أقراص", pluralUnitWord(res, DoseUnit.TABLET))
        assertEquals("كبسولات", pluralUnitWord(res, DoseUnit.CAPSULE))
    }

    @Test @Config(sdk = [35], qualifiers = "en") fun englishUnchanged() {
        assertEquals("1 tablet", t(1.0))
        assertEquals("2 tablets", t(2.0))
        assertEquals("0.5 tablets", t(0.5))
        assertEquals("1.5 tablets", t(1.5))
        assertEquals("1 ml", t(1.0, DoseUnit.ML))
    }
}
