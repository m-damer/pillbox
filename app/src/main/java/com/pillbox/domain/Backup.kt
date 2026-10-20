package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The backup file (".pillbox"): one UTF-8 JSON document with a format number.
 * Rows are kept as plain maps named like the database columns, so a newer app can add a column and an older
 * file still reads (missing fields use their defaults) and a file written by a newer format is refused, not guessed at.
 */
typealias Row = Map<String, Any?>

data class BackupData(
    val persons: List<Row>,
    val medicines: List<Row>,
    val schedules: List<Row>,
    val pauses: List<Row>,
    val doseEvents: List<Row>,
) {
    val summary get() = BackupSummary(persons.size, medicines.size, doseEvents.size)
}

data class BackupSummary(val people: Int, val medicines: Int, val doses: Int)

sealed interface BackupResult {
    data class Ok(val data: BackupData, val savedAt: LocalDateTime?) : BackupResult
    /** A newer Pill Box wrote this file. Nothing is changed. */
    data class Newer(val format: Int) : BackupResult
    /** Not a Pill Box backup, damaged, or rows that do not belong together. */
    data object Invalid : BackupResult
}

object BackupCodec {
    /** Bump only when old apps could not read the new file. Adding an optional field does not need a bump. */
    const val FORMAT = 1
    const val EXTENSION = "pillbox"
    private const val APP = "pillbox"

    fun fileName(date: LocalDate) = "pillbox-$date.$EXTENSION"

    fun encode(data: BackupData, savedAt: LocalDateTime): String {
        val root = linkedMapOf<String, Any?>(
            "app" to APP, "format" to FORMAT, "savedAt" to savedAt.withNano(0).toString(),
            "persons" to data.persons, "medicines" to data.medicines, "schedules" to data.schedules,
            "pauses" to data.pauses, "doseEvents" to data.doseEvents,
        )
        return MiniJson.write(root)
    }

    fun decode(text: String): BackupResult {
        val root = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *> ?: return BackupResult.Invalid
        if (root["app"] != APP) return BackupResult.Invalid
        val format = (root["format"] as? Number)?.toInt() ?: return BackupResult.Invalid
        if (format > FORMAT) return BackupResult.Newer(format)
        if (format < 1) return BackupResult.Invalid
        fun rows(key: String): List<Row>? {
            val list = root[key] as? List<*> ?: return null
            return list.map { r -> (r as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: return null }
        }
        val data = BackupData(
            rows("persons") ?: return BackupResult.Invalid, rows("medicines") ?: return BackupResult.Invalid,
            rows("schedules") ?: return BackupResult.Invalid, rows("pauses") ?: return BackupResult.Invalid,
            rows("doseEvents") ?: return BackupResult.Invalid,
        )
        if (!consistent(data)) return BackupResult.Invalid
        val savedAt = (root["savedAt"] as? String)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        return BackupResult.Ok(data, savedAt)
    }

    /** Every row points at a row that exists, and ids are unique, so the restore cannot half-fail. */
    internal fun consistent(d: BackupData): Boolean {
        fun ids(rows: List<Row>): Set<Long>? {
            val list = rows.map { (it["id"] as? Number)?.toLong() ?: return null }
            return list.toSet().takeIf { it.size == list.size }
        }
        val people = ids(d.persons) ?: return false
        val meds = ids(d.medicines) ?: return false
        ids(d.schedules) ?: return false
        ids(d.pauses) ?: return false
        ids(d.doseEvents) ?: return false
        fun pointsTo(rows: List<Row>, key: String, targets: Set<Long>) = rows.all { (it[key] as? Number)?.toLong() in targets }
        return pointsTo(d.medicines, "personId", people) && pointsTo(d.schedules, "medicineId", meds) &&
            pointsTo(d.pauses, "medicineId", meds) && pointsTo(d.doseEvents, "medicineId", meds)
    }

    private val named = Regex("""pillbox-\d{4}-\d{2}-\d{2}( \(\d+\))?\.$EXTENSION""")

    /** Backups Pill Box made itself, oldest first, past the [keep] newest ones: the ones to remove. Other files are never touched. */
    fun toRemove(names: List<String>, keep: Int): List<String> =
        names.filter { named.matches(it) }.sortedDescending().drop(keep)
}

/** When the last backup was made and how it went, for the Backup screen and the Settings row. */
enum class BackupProblem { NO_SPACE, FOLDER_UNAVAILABLE, OTHER }

data class BackupSettings(
    val autoFolder: Boolean = false,
    val folderUri: String? = null,
    val folderLabel: String? = null,
    val lastSuccessAt: LocalDateTime? = null,
    val lastProblem: BackupProblem? = null,
    val lastProblemAt: LocalDateTime? = null,
) {
    /** The last try failed and nothing worked since. */
    val failing get() = lastProblem != null && lastProblemAt != null && (lastSuccessAt == null || lastProblemAt > lastSuccessAt)
    val hasFolder get() = folderUri != null
}

/** A small JSON reader and writer, so the backup format needs no library and works in plain Kotlin tests. */
internal object MiniJson {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Number -> sb.append(v.toDouble().let { if (it == Math.rint(it) && Math.abs(it) < 1e15) it.toLong().toString() + ".0" else it.toString() })
            is String -> string(sb, v)
            is Map<*, *> -> { sb.append('{'); var first = true; v.forEach { (k, x) -> if (!first) sb.append(','); first = false; string(sb, k.toString()); sb.append(':'); append(sb, x) }; sb.append('}') }
            is List<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); append(sb, x) }; sb.append(']') }
            else -> string(sb, v.toString())
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) when {
            ch == '"' -> sb.append("\\\"")
            ch == '\\' -> sb.append("\\\\")
            ch == '\n' -> sb.append("\\n")
            ch == '\r' -> sb.append("\\r")
            ch == '\t' -> sb.append("\\t")
            ch < ' ' -> sb.append("\\u%04x".format(ch.code))
            else -> sb.append(ch)
        }
        sb.append('"')
    }

    fun parse(text: String): Any? = Reader(text).run { val v = value(); ws(); if (i != s.length) error("extra"); v }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) error("end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("bad")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) error("bad"); i += w.length; return v }
        fun num(): Number {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) error("end")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) error("end")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { if (i + 4 > s.length) error("end"); sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            '"', '\\', '/' -> sb.append(e)
                            else -> error("bad")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(value()); ws()
                if (i >= s.length) error("end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                error("bad")
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') error("bad")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') error("bad")
                i++
                out[k] = value(); ws()
                if (i >= s.length) error("end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                error("bad")
            }
        }
    }
}

object BackupFolders {
    /**
     * The readable path of a folder picked with the system folder picker, from its document id:
     * "primary:Documents/Pill Box" is "Documents / Pill Box". The top of a storage volume gives an empty path.
     */
    fun path(documentId: String): String =
        documentId.substringAfter(':', "").split('/').filter { it.isNotBlank() }.joinToString(" / ")
}
