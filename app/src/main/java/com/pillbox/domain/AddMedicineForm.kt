package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class FreqChoice { ONCE, TWICE, THREE, HOURS, AS_NEEDED }
enum class DaysChoice { EVERY_DAY, SPECIFIC, EVERY_N }
enum class StartChoice { TODAY, TOMORROW, PICK }
enum class LengthChoice { ONGOING, SEVEN, OTHER }
enum class StrengthUnit { MG, G, ML }
enum class FormError { NAME_MISSING, BOX_MISSING, DAYS_MISSING }

/**
 * Everything the 3-step Add medicine flow collects, and how it turns into a [Medicine] and a [Schedule].
 * Pure Kotlin so the rules are unit-tested without Android.
 */
data class AddMedicineForm(
    val name: String = "",
    val type: DoseUnit = DoseUnit.TABLET,
    val strength: String = "",
    val strengthUnit: StrengthUnit = StrengthUnit.MG,
    val dose: Double = 1.0,
    val boxCount: Int? = 30,
    val freq: FreqChoice = FreqChoice.TWICE,
    val firstDose: LocalTime = LocalTime.of(8, 0),
    /** Set when the user changed one of the automatic dose times. Cleared when frequency or first dose changes. */
    val customTimes: List<LocalTime>? = null,
    val hours: Int = 8,
    val maxPerDay: Int = 4,
    val minGapHours: Int = 6,
    val withFood: WithFood = WithFood.WITH_MEAL,
    val days: DaysChoice = DaysChoice.EVERY_DAY,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val everyN: Int = 2,
    val start: StartChoice = StartChoice.TODAY,
    val pickedDate: LocalDate? = null,
    val length: LengthChoice = LengthChoice.ONGOING,
    val lengthDays: Int = 10,
) {
    // ---- edits that reset dependent fields ----
    fun withType(t: DoseUnit) = copy(type = t, dose = defaultDose(t), boxCount = defaultBox(t))
    fun withFreq(f: FreqChoice) = copy(freq = f, customTimes = null)
    fun withFirstDose(t: LocalTime) = copy(firstDose = t, customTimes = null)

    fun withDoseTime(index: Int, time: LocalTime): AddMedicineForm {
        val list = times().toMutableList()
        if (index !in list.indices) return this
        list[index] = time
        return copy(customTimes = list)
    }

    // ---- derived ----
    /** Dose times for once, twice and 3 times a day. Empty for the other choices. */
    fun times(): List<LocalTime> = customTimes ?: when (freq) {
        FreqChoice.ONCE -> listOf(firstDose)
        FreqChoice.TWICE -> listOf(firstDose, firstDose.plusHours(12))
        FreqChoice.THREE -> listOf(firstDose, firstDose.plusHours(6), firstDose.plusHours(12))
        else -> emptyList()
    }

    fun hasDays() = freq != FreqChoice.AS_NEEDED

    fun startDate(today: LocalDate): LocalDate = when (start) {
        StartChoice.TODAY -> today
        StartChoice.TOMORROW -> today.plusDays(1)
        StartChoice.PICK -> pickedDate ?: today
    }

    fun endDate(today: LocalDate): LocalDate? {
        val s = startDate(today)
        return when (length) {
            LengthChoice.ONGOING -> null
            LengthChoice.SEVEN -> s.plusDays(6)
            LengthChoice.OTHER -> s.plusDays((lengthDays - 1).toLong())
        }
    }

    private fun dayPattern(today: LocalDate): DayPattern = when (days) {
        DaysChoice.EVERY_DAY -> DayPattern.EveryDay
        DaysChoice.SPECIFIC -> DayPattern.DaysOfWeek(weekdays)
        DaysChoice.EVERY_N -> DayPattern.EveryNDays(everyN.coerceAtLeast(2), startDate(today))
    }

    fun schedule(today: LocalDate): Schedule {
        val pattern = if (hasDays()) dayPattern(today) else DayPattern.EveryDay
        val frequency: Frequency = when (freq) {
            FreqChoice.ONCE, FreqChoice.TWICE, FreqChoice.THREE -> Frequency.FixedTimes(times().sorted(), pattern)
            FreqChoice.HOURS -> Frequency.EveryNHours(firstDose, hours, pattern)
            FreqChoice.AS_NEEDED -> Frequency.AsNeeded(minGapHours * 60, maxPerDay)
        }
        return Schedule(startDate(today), endDate(today), frequency, withFood)
    }

    fun medicine(personId: Long): Medicine {
        val strengthText = strength.trim().takeIf { it.isNotEmpty() }?.let { "$it ${strengthUnit.name.lowercase()}" }
        val box = boxCount ?: 0
        return Medicine(
            personId = personId, name = name.trim(), strength = strengthText, unit = type,
            doseAmount = dose, stock = box.toDouble(), packSize = box,
        )
    }

    // ---- validation ----
    fun step1Errors(): Set<FormError> = buildSet {
        if (name.isBlank()) add(FormError.NAME_MISSING)
        if ((boxCount ?: 0) < 1) add(FormError.BOX_MISSING)
    }

    fun step3Errors(): Set<FormError> = buildSet {
        if (hasDays() && days == DaysChoice.SPECIFIC && weekdays.isEmpty()) add(FormError.DAYS_MISSING)
    }

    /** True once the user typed or changed anything worth a "Leave without saving?" prompt. */
    fun isDirty() = name.isNotBlank() || strength.isNotBlank()

    companion object {
        fun defaultDose(t: DoseUnit) = when (t) {
            DoseUnit.ML -> 5.0
            DoseUnit.DROPS -> 2.0
            else -> 1.0
        }

        fun doseStep(t: DoseUnit) = if (t == DoseUnit.TABLET) 0.5 else 1.0

        fun defaultBox(t: DoseUnit) = when (t) {
            DoseUnit.ML -> 100
            DoseUnit.DROPS -> 10
            DoseUnit.INJECTION -> 5
            else -> 30
        }
    }
}
