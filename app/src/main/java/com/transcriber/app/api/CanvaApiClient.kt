package com.transcriber.app.api

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.transcriber.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import android.util.Base64 as AndroidBase64

class CanvaApiClient(private val app: Context) {

    companion object {
        const val REDIRECT_URI = "com.transcriber.app://canva/callback"
        private const val AUTH_URL  = "https://www.canva.com/api/oauth/authorize"
        private const val TOKEN_URL = "https://api.canva.com/rest/v1/oauth/token"
        private const val API_BASE  = "https://api.canva.com/rest/v1"

        // Survives the Chrome Custom Tab round-trip in-process; acceptable to lose on kill
        var pendingCodeVerifier: String? = null

        fun buildAuthUrl(clientId: String): String {
            val verifier = generateCodeVerifier()
            pendingCodeVerifier = verifier
            val challenge = generateCodeChallenge(verifier)
            return "$AUTH_URL?response_type=code" +
                "&client_id=${Uri.encode(clientId)}" +
                "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                "&scope=${Uri.encode("design:content:write design:meta:read")}" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256"
        }

        fun openAuthTab(context: Context, clientId: String) {
            val url = buildAuthUrl(clientId)
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return android.util.Base64.encodeToString(
                bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        }

        private fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return android.util.Base64.encodeToString(
                digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        }
    }

    private val settingsRepo = SettingsRepository(app)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── OAuth ─────────────────────────────────────────────────────────────────

    suspend fun handleOAuthCallback(code: String, clientId: String): Boolean {
        val verifier      = pendingCodeVerifier ?: return false
        pendingCodeVerifier = null
        val clientSecret  = settingsRepo.canvaClientSecret.first()
        val result = exchangeCode(code, verifier, clientId, clientSecret)
        if (result.isSuccess) {
            val t = result.getOrThrow()
            settingsRepo.updateCanvaAccessToken(t.accessToken)
            settingsRepo.updateCanvaRefreshToken(t.refreshToken)
            settingsRepo.updateCanvaTokenExpiry(System.currentTimeMillis() / 1000 + t.expiresIn - 60)
            return true
        }
        return false
    }

    private suspend fun exchangeCode(
        code: String, verifier: String, clientId: String, clientSecret: String
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        val body = "grant_type=authorization_code" +
            "&code=${Uri.encode(code)}" +
            "&code_verifier=${Uri.encode(verifier)}" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}"
        postForm(TOKEN_URL, body, basicAuth(clientId, clientSecret))
    }

    private suspend fun refreshToken(refreshToken: String, clientId: String, clientSecret: String): Result<TokenResponse> =
        withContext(Dispatchers.IO) {
            val body = "grant_type=refresh_token&refresh_token=${Uri.encode(refreshToken)}"
            postForm(TOKEN_URL, body, basicAuth(clientId, clientSecret))
        }

    private fun basicAuth(clientId: String, clientSecret: String): String {
        val credentials = "$clientId:$clientSecret"
        return "Basic " + AndroidBase64.encodeToString(
            credentials.toByteArray(Charsets.UTF_8),
            AndroidBase64.NO_WRAP
        )
    }

    private fun postForm(url: String, formBody: String, authHeader: String?): Result<TokenResponse> {
        val request = Request.Builder()
            .url(url)
            .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .apply { if (authHeader != null) addHeader("Authorization", authHeader) }
            .build()
        return try {
            val response = http.newCall(request).execute()
            val bodyStr = response.body?.string() ?: return Result.failure(IOException("Empty response"))
            if (!response.isSuccessful) return Result.failure(IOException("HTTP ${response.code}: $bodyStr"))
            Result.success(gson.fromJson(bodyStr, TokenResponse::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns a valid access token, refreshing if needed. Null if not connected. */
    suspend fun getValidToken(): String? {
        val accessToken = settingsRepo.canvaAccessToken.first()
        if (accessToken.isBlank()) return null

        val expiry     = settingsRepo.canvaTokenExpiry.first()
        val now        = System.currentTimeMillis() / 1000
        if (expiry > now) return accessToken

        val refresh      = settingsRepo.canvaRefreshToken.first()
        val clientId     = settingsRepo.canvaClientId.first()
        val clientSecret = settingsRepo.canvaClientSecret.first()
        if (refresh.isBlank() || clientId.isBlank() || clientSecret.isBlank()) return null

        val result = refreshToken(refresh, clientId, clientSecret)
        if (result.isFailure) return null
        val t = result.getOrThrow()
        settingsRepo.updateCanvaAccessToken(t.accessToken)
        settingsRepo.updateCanvaRefreshToken(t.refreshToken)
        settingsRepo.updateCanvaTokenExpiry(now + t.expiresIn - 60)
        return t.accessToken
    }

    suspend fun clearTokens() {
        settingsRepo.updateCanvaAccessToken("")
        settingsRepo.updateCanvaRefreshToken("")
        settingsRepo.updateCanvaTokenExpiry(0)
    }

    // ── Import API ────────────────────────────────────────────────────────────

    /** Uploads PPTX to Canva Import API and returns the edit URL of the created design. */
    suspend fun importDesign(
        accessToken: String, pptxBytes: ByteArray, title: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("title_type", "asset_name")
            .addFormDataPart(
                "import_file", "$title.pptx",
                pptxBytes.toRequestBody(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation".toMediaType()
                )
            )
            .build()

        val request = Request.Builder()
            .url("$API_BASE/imports")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipart)
            .build()

        try {
            val response = http.newCall(request).execute()
            val bodyStr  = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
            if (!response.isSuccessful) return@withContext Result.failure(IOException("HTTP ${response.code}: $bodyStr"))

            val importResp = gson.fromJson(bodyStr, ImportResponse::class.java)
            val jobId = importResp.job?.id ?: return@withContext Result.failure(IOException("No job ID in response"))

            pollImportJob(accessToken, jobId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun pollImportJob(accessToken: String, jobId: String): Result<String> =
        withContext(Dispatchers.IO) {
            repeat(30) { attempt ->
                delay(if (attempt < 3) 2_000L else 4_000L)
                val request = Request.Builder()
                    .url("$API_BASE/imports/$jobId")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                try {
                    val response = http.newCall(request).execute()
                    val bodyStr  = response.body?.string() ?: return@withContext Result.failure(IOException("Empty poll response"))
                    if (!response.isSuccessful) return@withContext Result.failure(IOException("Poll HTTP ${response.code}: $bodyStr"))

                    val pollResp = gson.fromJson(bodyStr, ImportResponse::class.java)
                    when (pollResp.job?.status) {
                        "success" -> {
                            val editUrl = pollResp.job.result?.design?.urls?.editUrl
                                ?: return@withContext Result.failure(IOException("No edit URL in result"))
                            return@withContext Result.success(editUrl)
                        }
                        "failed"  -> return@withContext Result.failure(
                            IOException(pollResp.job.error?.message ?: "Import failed")
                        )
                        else      -> { /* in_progress — keep polling */ }
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            Result.failure(IOException("Import timed out after 30 polls"))
        }

    // ── Data classes ──────────────────────────────────────────────────────────

    private data class TokenResponse(
        @SerializedName("access_token")  val accessToken:  String = "",
        @SerializedName("refresh_token") val refreshToken: String = "",
        @SerializedName("expires_in")    val expiresIn:    Long   = 3600
    )

    private data class ImportResponse(val job: ImportJob? = null)
    private data class ImportJob(
        val id: String? = null,
        val status: String? = null,
        val error: ImportError? = null,
        val result: ImportResult? = null
    )
    private data class ImportError(val message: String? = null)
    private data class ImportResult(val design: ImportDesign? = null)
    private data class ImportDesign(val urls: ImportUrls? = null)
    private data class ImportUrls(
        @SerializedName("edit_url") val editUrl: String? = null
    )
}
