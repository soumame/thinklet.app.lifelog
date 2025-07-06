package ai.fd.thinklet.library.lifelog.data.http.impl

import ai.fd.thinklet.library.lifelog.data.http.HttpUploadRepository
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HttpUploadRepositoryImpl @Inject constructor() : HttpUploadRepository {

    companion object {
        private const val TAG = "HttpUploadRepositoryImpl"
    }

    private var uploadUrl: String? = null
    private var apiKey: String? = null
    private var isConfigured = false
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun configure(uploadUrl: String, apiKey: String) {
        this.uploadUrl = uploadUrl
        this.apiKey = apiKey
        this.isConfigured = true
        Log.i(TAG, "HTTP upload configured successfully. URL: $uploadUrl")
    }

    override suspend fun uploadFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("HTTP upload is not configured"))
            }
            
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))
            }

            val url = uploadUrl ?: return@withContext Result.failure(IllegalStateException("Upload URL is null"))
            val key = apiKey ?: return@withContext Result.failure(IllegalStateException("API key is null"))

            // ファイル拡張子に基づいてContent-Typeを決定
            val contentType = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "mp3" -> "audio/mp4"  // 実際はAAC/M4A形式のため
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                "raw" -> "audio/raw"
                else -> "application/octet-stream"
            }.toMediaType()

            // マルチパートボディを作成
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(contentType)
                )
                .build()

            // リクエストを作成
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", key)
                .post(requestBody)
                .build()

            Log.d(TAG, "Uploading file: ${file.name} to URL: $url")
            
            // リクエストを実行
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.i(TAG, "File uploaded successfully: ${file.name}")
                Result.success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to upload file. Status: ${response.code}, Error: $errorBody")
                Result.failure(Exception("HTTP upload failed with status ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: ${file.absolutePath}", e)
            Result.failure(e)
        }
    }

    override fun isConfigured(): Boolean {
        return isConfigured && uploadUrl != null && apiKey != null
    }
}