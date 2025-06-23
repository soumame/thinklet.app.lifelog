package ai.fd.thinklet.library.lifelog.data.s3.impl

import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class S3UploadRepositoryImpl @Inject constructor() : S3UploadRepository {

    companion object {
        private const val TAG = "S3UploadRepositoryImpl"
    }

    private var s3Client: S3Client? = null
    private var bucketName: String? = null
    private var customEndpoint: String? = null
    private var isConfigured = false

    override fun configure(bucketName: String, region: String, accessKey: String, secretKey: String, endpoint: String?) {
        try {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            val credentialsProvider = StaticCredentialsProvider.create(credentials)
            
            val clientBuilder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .httpClient(UrlConnectionHttpClient.builder().build()) // Android対応HTTPクライアント
            
            // カスタムエンドポイントが指定されている場合は設定
            if (!endpoint.isNullOrEmpty()) {
                clientBuilder.endpointOverride(java.net.URI.create(endpoint))
                // S3互換サービスの場合、Regionは"us-east-1"をデフォルトに設定
                clientBuilder.region(Region.US_EAST_1)
                // Path-style accessを有効化（S3互換サービス用）
                clientBuilder.serviceConfiguration { builder -> 
                    builder.pathStyleAccessEnabled(true)
                }
                Log.i(TAG, "Using custom S3 endpoint: $endpoint with path-style access")
            } else {
                // AWS S3の場合は指定されたリージョンを使用
                clientBuilder.region(Region.of(region))
                Log.i(TAG, "Using AWS S3 with region: $region")
            }
            
            this.s3Client = clientBuilder.build()
            
            this.bucketName = bucketName
            this.customEndpoint = endpoint
            this.isConfigured = true
            
            val endpointInfo = if (endpoint.isNullOrEmpty()) "AWS S3" else "Custom endpoint: $endpoint"
            Log.i(TAG, "S3 configured successfully. Bucket: $bucketName, Region: $region, Endpoint: $endpointInfo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure S3", e)
            this.isConfigured = false
        }
    }

    override suspend fun uploadFile(file: File, keyPrefix: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("S3 is not configured"))
            }
            
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))
            }

            val client = s3Client ?: return@withContext Result.failure(IllegalStateException("S3 client is null"))
            val bucket = bucketName ?: return@withContext Result.failure(IllegalStateException("Bucket name is null"))

            // S3オブジェクトキーを生成（lifelog/YYYY/MM/DD/filename.jpg）
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val datePath = dateFormat.format(Date())
            val objectKey = if (keyPrefix.isNotEmpty()) {
                "$keyPrefix/$datePath/${file.name}"
            } else {
                "lifelog/$datePath/${file.name}"
            }

            // S3にアップロード
            // ファイル拡張子に基づいてContent-Typeを決定
            val contentType = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "mp3" -> "audio/mpeg"
                "raw" -> "audio/raw"
                else -> "application/octet-stream"
            }

            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build()

            val requestBody = RequestBody.fromFile(file)
            Log.d(TAG, "Uploading file: ${file.name} to bucket: $bucket, key: $objectKey")
            
            val response = client.putObject(putObjectRequest, requestBody)

            // カスタムエンドポイントの場合はそのURLを、AWS S3の場合は標準URLを返す
            val s3Url = if (!customEndpoint.isNullOrEmpty()) {
                "$customEndpoint/$bucket/$objectKey"
            } else {
                "https://$bucket.s3.amazonaws.com/$objectKey"  
            }
            Log.i(TAG, "File uploaded successfully to S3: $s3Url")
            
            Result.success(s3Url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file to S3: ${file.absolutePath}", e)
            Result.failure(e)
        }
    }

    override fun isConfigured(): Boolean {
        return isConfigured && s3Client != null && bucketName != null
    }
}