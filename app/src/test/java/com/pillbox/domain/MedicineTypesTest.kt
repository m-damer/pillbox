package com.pillbox.domain

import org.junit.Assert.*
import org.junit.Test

class MedicineTypesTest {
    @Test fun everyTypeHasSensibleDefaults() {
        for (t in DoseUnit.entries) {
            assertTrue("$t dose", AddMedicineForm.defaultDose(t) > 0.0)
            assertTrue("$t box", AddMedicineForm.defaultBox(t) > 0)
            assertTrue("$t step", AddMedicineForm.doseStep(t) > 0.0)
        }
    }
    @Test fun oldNamesStillExist() {
        // Saved data uses these names; they must never change.
        for (n in listOf("TABLET", "CAPSULE", "ML", "DROPS", "INJECTION", "OTHER")) assertNotNull(DoseUnit.valueOf(n))
    }
    @Test fun fifteenTypesWithOtherLast() {
        assertEquals(15, DoseUnit.entries.size)
        assertEquals(DoseUnit.OTHER, DoseUnit.entries.last())
        assertEquals(DoseUnit.TABLET, DoseUnit.entries.first())
    }
    @Test fun packKinds() {
        assertEquals(PackKind.BOTTLE, MedicineTypes.packKind(DoseUnit.ML))
        assertEquals(PackKind.BOTTLE, MedicineTypes.packKind(DoseUnit.DROPS))
        assertEquals(PackKind.TUBE, MedicineTypes.packKind(DoseUnit.CREAM))
        assertEquals(PackKind.INHALER, MedicineTypes.packKind(DoseUnit.INHALER))
        assertEquals(PackKind.BOX, MedicineTypes.packKind(DoseUnit.PATCH))
        assertTrue(MedicineTypes.isEstimate(DoseUnit.GEL))
        assertFalse(MedicineTypes.isEstimate(DoseUnit.TABLET))
    }
    @Test fun changingTypeResetsDoseAndBox() {
        val f = AddMedicineForm().withType(DoseUnit.INHALER)
        assertEquals(2.0, f.dose, 0.0)
        assertEquals(120, f.boxCount)
    }
}
