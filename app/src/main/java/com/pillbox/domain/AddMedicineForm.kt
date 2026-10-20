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
    /** Edit mode: the start date cannot change. */
    val fixedStart: LocalDate? = null,
    /** Edit mode: where "every N days" counting began, kept unless the user changes the day pattern. */
    val everyNAnchor: LocalDate? = null,
    /** Edit mode: the stock the medicine had when the form was opened. Null when adding. */
    val originalStock: Double? = null,
    val originalPackSize: Int? = null,
) {
    val isEdit get() = originalStock != null

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
        FreqChoice.THREE -> listOf(firstDose, firstDose.plusHours(8), firstDose.plusHours(16))
        else -> emptyList()
    }

    fun hasDays() = freq != FreqChoice.AS_NEEDED

    fun startDate(today: LocalDate): LocalDate = fixedStart ?: when (start) {
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
        DaysChoice.EVERY_N -> DayPattern.EveryNDays(everyN.coerceAtLeast(2), everyNAnchor ?: maxOf(startDate(today), today))
    }

    fun schedule(today: LocalDate): Schedule {
        val pattern = if (hasDays()) dayPattern(today) else DayPattern.EveryDay
        val frequency: Frequency = when (freq) {
            FreqChoice.ONCE, FreqChoice.TWICE, FreqChoice.THREE -> Frequency.FixedTimes(times().sorted(), pattern)
            FreqChoice.HOURS -> Frequency.EveryNHours(firstDose, hours, pattern)
            FreqChoice.AS_NEEDED -> Frequency.AsNeeded(minGapHours * 60, maxPerDay)
        }
        val startTime = if (freq == FreqChoice.ONCE || freq == FreqChoice.TWICE || freq == FreqChoice.THREE) times().firstOrNull() else null
        return Schedule(startDate(today), endDate(today), frequency, withFood, startTime)
    }

    fun medicine(personId: Long): Medicine {
        val strengthText = strength.trim().takeIf { it.isNotEmpty() }?.let { "$it ${strengthUnit.name.lowercase()}" }
        val box = boxCount ?: 0
        return Medicine(
            personId = personId, name = name.trim(), strength = strengthText, unit = type,
            doseAmount = dose, stock = box.toDouble(), packSize = box,
        )
    }

    /**
     * The medicine after an edit. Stock is kept exactly as it was unless the user changed the number
     * (the form shows whole units, so 7.5 tablets would otherwise round).
     */
    fun updated(existing: Medicine): Medicine {
        val strengthText = strength.trim().takeIf { it.isNotEmpty() }?.let { "$it ${strengthUnit.name.lowercase()}" }
        val shown = originalStock?.let { Math.round(it).toInt() }
        val box = boxCount ?: 0
        return existing.copy(
            name = name.trim(), strength = strengthText, unit = type, doseAmount = dose,
            stock = if (originalStock != null && box == shown) originalStock else box.toDouble(),
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
        /** Fills the form from a saved medicine, for Edit medicine. */
        fun fromExisting(m: Medicine, s: Schedule): AddMedicineForm {
            val parts = m.strength?.trim()?.split(" ", limit = 2)
            val unit = parts?.getOrNull(1)?.let { u -> StrengthUnit.values().firstOrNull { it.name.equals(u, ignoreCase = true) } }
            val (strengthNumber, strengthUnit) = when {
                m.strength.isNullOrBlank() -> "" to StrengthUnit.MG
                unit != null -> parts[0] to unit
                else -> m.strength to StrengthUnit.MG
            }
            var f = AddMedicineForm(
                name = m.name, type = m.unit, strength = strengthNumber, strengthUnit = strengthUnit, dose = m.doseAmount,
                boxCount = Math.round(m.stock).toInt().coerceAtLeast(0).takeIf { it > 0 },
                withFood = s.withFood, fixedStart = s.start, originalStock = m.stock, originalPackSize = m.packSize,
            )
            val days: DayPattern? = when (val fr = s.frequency) {
                is Frequency.FixedTimes -> {
                    val freq = when (fr.times.size) { 1 -> FreqChoice.ONCE; 2 -> FreqChoice.TWICE; else -> FreqChoice.THREE }
                    val first = s.startTime ?: fr.times.minOrNull() ?: LocalTime.of(8, 0)
                    f = f.copy(freq = freq, firstDose = first)
                    if (f.times().sorted() != fr.times.sorted()) f = f.copy(customTimes = fr.times.sorted())
                    fr.days
                }
                is Frequency.EveryNHours -> { f = f.copy(freq = FreqChoice.HOURS, firstDose = fr.first, hours = fr.hours); fr.days }
                is Frequency.AsNeeded -> {
                    f = f.copy(freq = FreqChoice.AS_NEEDED, maxPerDay = fr.maxPerDay ?: 4, minGapHours = (fr.minGapMinutes / 60).coerceAtLeast(1))
                    null
                }
            }
            when (days) {
                is DayPattern.DaysOfWeek -> f = f.copy(days = DaysChoice.SPECIFIC, weekdays = days.days)
                is DayPattern.EveryNDays -> f = f.copy(days = DaysChoice.EVERY_N, everyN = days.n, everyNAnchor = days.anchor)
                else -> Unit
            }
            val end = s.end
            if (end == null) f = f.copy(length = LengthChoice.ONGOING)
            else {
                val n = (java.time.temporal.ChronoUnit.DAYS.between(s.start, end) + 1).toInt().coerceAtLeast(1)
                f = f.copy(length = if (n == 7) LengthChoice.SEVEN else LengthChoice.OTHER, lengthDays = n)
            }
            return f
        }

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
