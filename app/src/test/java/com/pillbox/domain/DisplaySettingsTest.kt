package com.pillbox.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySettingsTest {
    private val none = DisplaySettings()

    @Test fun digitsFollowTheLanguageUntilChosen() {
        assertTrue(none.useArabicDigits("ar"))
        assertFalse(none.useArabicDigits("en"))
    }

    @Test fun chosenDigitsWinOverTheLanguage() {
        assertFalse(none.copy(arabicDigits = false).useArabicDigits("ar"))
        assertTrue(none.copy(arabicDigits = true).useArabicDigits("en"))
    }

    @Test fun timeFormatFollowsThePhoneUnlessChosen() {
        assertTrue(none.is24Hour(true)); assertFalse(none.is24Hour(false))
        assertFalse(none.copy(timeFormat = TimeFormat.H12).is24Hour(true))
        assertTrue(none.copy(timeFormat = TimeFormat.H24).is24Hour(false))
    }

    @Test fun weekStartFollowsTheLanguageUnlessChosen() {
        assertEquals(DayOfWeek.SATURDAY, none.firstDay(DayOfWeek.SATURDAY))
        assertEquals(DayOfWeek.MONDAY, none.copy(firstDay = DayOfWeek.MONDAY).firstDay(DayOfWeek.SATURDAY))
    }

    @Test fun textSizeMultipliesThePhonesSizeButStopsAtTwice() {
        assertEquals(1f, none.fontScale(1f), 0.001f)
        assertEquals(1.3f, none.copy(textSize = TextSize.LARGEST).fontScale(1f), 0.001f)
        assertEquals(1.5f * 1.15f, none.copy(textSize = TextSize.LARGE).fontScale(1.5f), 0.001f)
        assertEquals(2f, none.copy(textSize = TextSize.LARGEST).fontScale(1.8f), 0.001f)
    }

    @Test fun aPhoneSizeAboveTwiceIsNeverShrunk() = assertEquals(2.5f, none.copy(textSize = TextSize.LARGEST).fontScale(2.5f), 0.001f)

    @Test fun unknownPhoneLanguagesShowEnglish() {
        assertEquals("ar", DisplaySettings.supported("ar")); assertEquals("en", DisplaySettings.supported("fr")); assertEquals("en", DisplaySettings.supported(null))
    }
}
