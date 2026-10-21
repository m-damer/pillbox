package com.pillbox.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.pillbox.domain.DriveApi
import com.pillbox.domain.DriveFile
import com.pillbox.domain.DriveProblem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Pill Box's own private folder in the person's Google Drive ("appDataFolder"). The app can only see files it made itself.
 * Sign-in uses Google's authorization screen; no password or key is ever kept by Pill Box, and the developer never sees the files.
 */
class DriveClient(private val context: Context) {

    sealed interface Auth {
        data class Ready(val token: String, val email: String?) : Auth
        /** The person must pick an account or agree once. Only possible with a screen in front. */
        data class NeedsConsent(val intentSender: IntentSender) : Auth
        data class Failed(val problem: DriveProblem) : Auth
    }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Problem(val problem: DriveProblem) : Result<Nothing>
    }

    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveApi.SCOPE))).build()

    /** A fresh access token if the person already agreed; otherwise the screen to ask them. */
    suspend fun authorize(): Auth = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { r -> if (cont.isActive) cont.resume(toAuth(r)) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resume(Auth.Failed(problemOf(e))) }
    }

    /**
     * Withdraws Pill Box's permission for [email]. Turning Drive off must do this, or Google keeps the permission and
     * turning Drive on again silently picks the same account instead of showing the account chooser.
     * Failing (no internet, no email known) is not an error: the person can still turn Drive off.
     */
    suspend fun revoke(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val revoke = com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
            .setAccount(android.accounts.Account(email, "com.google"))
            .setScopes(listOf(Scope(DriveApi.SCOPE)))
            .build()
        return suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context).revokeAccess(revoke)
                .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                .addOnFailureListener { if (cont.isActive) cont.resume(false) }
        }
    }

    /** What came back from the authorization screen. */
    fun fromIntent(data: Intent?): Auth = try {
        toAuth(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        Auth.Failed(problemOf(e))
    }

    private fun toAuth(r: AuthorizationResult): Auth {
        val pending = r.pendingIntent
        if (r.hasResolution() && pending != null) return Auth.NeedsConsent(pending.intentSender)
        val token = r.accessToken ?: return Auth.Failed(DriveProblem.SIGN_IN)
        return Auth.Ready(token, runCatching { r.toGoogleSignInAccount()?.email }.getOrNull())
    }

    private fun problemOf(e: Exception): DriveProblem {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> DriveProblem.NO_INTERNET
            CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.CANCELED -> DriveProblem.SIGN_IN
            else -> if (e is IOException) DriveProblem.NO_INTERNET else DriveProblem.OTHER
        }
    }

    /** Saves [text] as [name], replaces a file of the same name, and keeps only the newest [keep] backups. */
    suspend fun upload(token: String, name: String, text: String, keep: Int): Result<Unit> = call {
        val all = list(token)
        val others = all.filter { it.name != name }
        // The same day twice replaces the file.
        all.filter { it.name == name }.forEach { delete(token, it.id) }
        val body = DriveApi.multipartBody(name, text)
        val (code, _) = http("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token, body, "multipart/related; boundary=${DriveApi.BOUNDARY}")
        if (code !in 200..299) throw HttpFailure(code)
        // The new file is the newest, so (keep - 1) of the others stay.
        DriveApi.toRemove(others, keep - 1).forEach { runCatching { delete(token, it.id) } }
    }

    /** The newest backup in Drive and its text, or null when there is none. */
    suspend fun newest(token: String): Result<Pair<DriveFile, String>?> = call {
        val file = list(token).firstOrNull() ?: return@call null
        val (code, body) = http("GET", "https://www.googleapis.com/drive/v3/files/${enc(file.id)}?alt=media", token)
        if (code !in 200..299) throw HttpFailure(code)
        file to body
    }

    private class HttpFailure(val code: Int) : Exception()

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.Ok(block())
        } catch (e: HttpFailure) {
            Result.Problem(DriveApi.problemFor(e.code, offline = false))
        } catch (e: IOException) {
            Result.Problem(DriveApi.problemFor(null, offline = true))
        } catch (e: Exception) {
            Result.Problem(DriveProblem.OTHER)
        }
    }

    private fun list(token: String): List<DriveFile> {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&orderBy=createdTime%20desc&pageSize=100&fields=" + enc("files(id,name,createdTime)")
        val (code, body) = http("GET", url, token)
        if (code !in 200..299) throw HttpFailure(code)
        return DriveApi.parseFiles(body)
    }

    private fun delete(token: String, id: String) {
        val (code, _) = http("DELETE", "https://www.googleapis.com/drive/v3/files/${enc(id)}", token)
        if (code !in 200..299 && code != 404) throw HttpFailure(code)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun http(method: String, url: String, token: String, body: String? = null, contentType: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
