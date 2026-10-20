package com.pillbox.domain

/** Rules for the People screens. Pure Kotlin, so they are unit tested without Android. */
object PeopleRules {
    const val MAX_NAME = 30
    /** Avatar colors the user can pick; the first is the default. The actual colors are in the theme. */
    const val COLORS = 6
    const val MIN_BIRTH_YEAR = 1900

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    fun canSave(raw: String): Boolean = cleanName(raw).isNotEmpty()

    fun colorFor(index: Int): Int = ((index % COLORS) + COLORS) % COLORS

    /** The next color to suggest for a new person: the first one nobody uses, otherwise the least used. */
    fun nextColor(used: List<Int>): Int = (0 until COLORS).minByOrNull { c -> used.count { colorFor(it) == c } } ?: 0

    /**
     * Avatar letters for a list of display names, in order: the first letter of each name; when two people would
     * share the same letter, the later ones get two letters ("M" for Me, "Mo" for Mom).
     */
    fun initials(names: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return names.map { raw ->
            val chars = raw.trim().codePoints().toArray().map { String(Character.toChars(it)) }.filter { it.isNotBlank() }
            val one = chars.firstOrNull()?.uppercase().orEmpty()
            val letters = if (one in seen && chars.size > 1) one + chars[1].lowercase() else one
            seen += one
            letters
        }
    }

    /** A birth year the user typed: empty means none; otherwise it must be a real year up to [currentYear]. */
    fun parseBirthYear(raw: String, currentYear: Int): Result<Int?> {
        val s = raw.trim()
        if (s.isEmpty()) return Result.success(null)
        val y = s.toIntOrNull()
        return if (y != null && y in MIN_BIRTH_YEAR..currentYear) Result.success(y) else Result.failure(IllegalArgumentException("year"))
    }

    /** What the People list says about one person's day. */
    fun summarize(model: TodayModel): DaySummary {
        val missed = model.groups.flatMap { it.rows }.filter { it.status == RowStatus.PENDING && it.missed }
        return DaySummary(model.taken, model.total, missed.size, missed.minOfOrNull { it.planned })
    }
}

data class DaySummary(val taken: Int, val total: Int, val missed: Int, val firstMissed: java.time.LocalDateTime?)
