package com.pillbox.domain

/** A newer version found on the releases page. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val sha256Url: String?,
    val sizeBytes: Long,
)

/** What the phone remembers about updates. On this phone only. */
data class UpdateSettings(
    val enabled: Boolean = true,
    val lastCheckMillis: Long = 0L,
    val lastNotifiedCode: Int = 0,
    val available: UpdateInfo? = null,
)

object UpdateRules {
    /** "v1.2.3" or "1.2.3" becomes 10203 (major*10000 + minor*100 + patch), the same formula the release workflow uses. Null if it is not a plain version. */
    fun versionCode(tag: String): Int? {
        val parts = tag.trim().removePrefix("v").removePrefix("V").split(".")
        if (parts.size !in 2..3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.drop(1).any { it < 0 || it > 99 } || nums[0] < 0) return null
        return nums[0] * 10000 + nums[1] * 100 + (nums.getOrNull(2) ?: 0)
    }

    fun isNewer(availableCode: Int, installedCode: Int) = availableCode > installedCode

    /** One quiet notification per version: only when the version is newer than the app and has not been announced yet. */
    fun shouldNotify(enabled: Boolean, info: UpdateInfo, installedCode: Int, lastNotifiedCode: Int) =
        enabled && isNewer(info.versionCode, installedCode) && info.versionCode > lastNotifiedCode

    /** The first 64 hex characters of a ".sha256" file ("hash  filename" or just the hash), lower case. Null if it does not look like one. */
    fun parseSha256(text: String): String? {
        val token = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return null
        return token.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** Release notes can be long; the screen shows the first lines. Markdown marks and blank lines are dropped. */
    fun plainNotes(markdown: String, maxLines: Int = 8): String =
        markdown.lines().map { it.trim().trimStart('#', '*', '-', ' ').trim() }.filter { it.isNotEmpty() }.take(maxLines).joinToString("\n")
}
