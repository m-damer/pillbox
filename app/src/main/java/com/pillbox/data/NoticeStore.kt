package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.Notice
import com.pillbox.domain.NoticeKind
import com.pillbox.domain.NoticeRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.noticePrefs by preferencesDataStore(name = "notice_prefs")

/**
 * The notifications screen's history, saved on this phone only (not in backups: it is a log, not your medicine data).
 * Every change goes through DataStore's atomic edit, so a reminder and the screen can write at the same time.
 */
class NoticeStore(private val context: Context) {
    private val data = stringPreferencesKey("notices")

    val flow: Flow<List<Notice>> = context.noticePrefs.data.map { decode(it[data]) }

    suspend fun record(n: Notice, nowMillis: Long = System.currentTimeMillis()) = change { NoticeRules.add(it, n, nowMillis) }
    suspend fun markAllRead() = change { NoticeRules.markAllRead(it) }
    suspend fun markRead(key: String) = change { NoticeRules.markRead(it, key) }
    suspend fun clear(kind: NoticeKind?) = change { NoticeRules.clear(it, kind) }
    suspend fun deleteAll() { context.noticePrefs.edit { it.clear() } }

    private suspend fun change(f: (List<Notice>) -> List<Notice>) {
        context.noticePrefs.edit { p -> p[data] = encode(f(decode(p[data]))) }
    }

    companion object {
        fun encode(list: List<Notice>): String = JSONArray().also { a ->
            list.forEach { n ->
                a.put(JSONObject().put("k", n.key).put("t", n.kind.name).put("h", n.title).put("x", n.text).put("a", n.atMillis).put("r", n.read).put("g", n.target ?: JSONObject.NULL).put("p", n.personId ?: JSONObject.NULL))
            }
        }.toString()

        /** A damaged or empty value gives an empty list; one bad row is skipped. */
        fun decode(s: String?): List<Notice> {
            if (s.isNullOrBlank()) return emptyList()
            val a = runCatching { JSONArray(s) }.getOrNull() ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val o = a.getJSONObject(i)
                    Notice(
                        key = o.getString("k"), kind = NoticeKind.valueOf(o.getString("t")), title = o.getString("h"), text = o.getString("x"),
                        atMillis = o.getLong("a"), read = o.optBoolean("r", false), target = if (o.isNull("g")) null else o.getString("g"),
                        personId = if (o.isNull("p")) null else o.optLong("p"),
                    )
                }.getOrNull()
            }
        }
    }
}
