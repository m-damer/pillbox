package com.pillbox.domain

/** The few Google Drive calls Pill Box makes, as plain text in and out, so they can be tested without a phone. */
enum class DriveProblem { NO_INTERNET, SIGN_IN, OTHER }

data class DriveFile(val id: String, val name: String, val createdTime: String)

object DriveApi {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val BOUNDARY = "pillbox-backup-boundary"

    /** The body of a multipart upload into the app's private Drive folder (only Pill Box can see it). */
    fun multipartBody(name: String, content: String, boundary: String = BOUNDARY): String {
        val meta = MiniJson.write(linkedMapOf("name" to name, "parents" to listOf("appDataFolder")))
        return "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n$content\r\n--$boundary--"
    }

    /** Backups in a "files.list" answer, newest first. Anything that is not a .pillbox file is ignored. */
    fun parseFiles(json: String): List<DriveFile> {
        val root = runCatching { MiniJson.parse(json) }.getOrNull() as? Map<*, *> ?: return emptyList()
        val files = root["files"] as? List<*> ?: return emptyList()
        return files.mapNotNull { f ->
            val m = f as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            if (!name.endsWith("." + BackupCodec.EXTENSION)) return@mapNotNull null
            DriveFile(id, name, m["createdTime"] as? String ?: "")
        }.sortedWith(compareByDescending<DriveFile> { it.createdTime }.thenByDescending { it.name })
    }

    /** Old backups to remove so Drive keeps only the newest [keep]. [files] must be newest first. */
    fun toRemove(files: List<DriveFile>, keep: Int): List<DriveFile> = files.drop(keep)

    /** How a failed call should be told to the person. */
    fun problemFor(httpCode: Int?, offline: Boolean): DriveProblem = when {
        offline -> DriveProblem.NO_INTERNET
        httpCode == 401 -> DriveProblem.SIGN_IN
        else -> DriveProblem.OTHER
    }
}
