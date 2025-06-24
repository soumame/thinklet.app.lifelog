package ai.fd.thinklet.library.lifelog.data.upload.impl

import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadQueueRepositoryImpl @Inject constructor(
    private val context: Context,
    private val networkRepository: NetworkRepository,
    private val s3UploadRepository: S3UploadRepository
) : UploadQueueRepository {

    companion object {
        private const val TAG = "UploadQueueRepositoryImpl"
        private const val QUEUE_FILE_NAME = "upload_queue.txt"
    }

    private val pendingUploads = ConcurrentLinkedQueue<File>()
    private val queueFile = File(context.filesDir, QUEUE_FILE_NAME)

    init {
        loadQueueFromFile()
    }

    override suspend fun enqueueFile(file: File) = withContext(Dispatchers.IO) {
        if (!pendingUploads.contains(file)) {
            pendingUploads.add(file)
            saveQueueToFile()
            Log.d(TAG, "File added to upload queue: ${file.absolutePath}")
        }
    }

    override suspend fun getPendingFiles(): List<File> = withContext(Dispatchers.IO) {
        // 存在しないファイルを除去
        val validFiles = pendingUploads.filter { it.exists() }
        if (validFiles.size != pendingUploads.size) {
            pendingUploads.clear()
            pendingUploads.addAll(validFiles)
            saveQueueToFile()
        }
        validFiles
    }

    override suspend fun removeFile(file: File) = withContext(Dispatchers.IO) {
        if (pendingUploads.remove(file)) {
            saveQueueToFile()
            Log.d(TAG, "File removed from upload queue: ${file.absolutePath}")
        }
    }

    override suspend fun processPendingUploads() = withContext(Dispatchers.IO) {
        if (!networkRepository.isWifiConnected()) {
            Log.d(TAG, "Not connected to WiFi, skipping upload processing")
            return@withContext
        }

        if (!s3UploadRepository.isConfigured()) {
            Log.d(TAG, "S3 not configured, skipping upload processing")
            return@withContext
        }

        if (!networkRepository.hasInternetConnection()) {
            Log.d(TAG, "No internet connection, skipping upload processing")
            return@withContext
        }

        val filesToUpload = getPendingFiles()
        Log.i(TAG, "Processing ${filesToUpload.size} pending uploads")

        filesToUpload.forEach { file ->
            try {
                // ファイル拡張子に基づいてS3キープレフィックスを決定
                val keyPrefix = when (file.extension.lowercase()) {
                    "mp3", "m4a", "aac", "wav" -> "audio"
                    "jpg", "jpeg", "png", "gif" -> "" // 画像は既存のロジックでlifelog/YYYY/MM/DDパスが使用される
                    else -> ""
                }
                
                val uploadResult = if (keyPrefix.isNotEmpty()) {
                    s3UploadRepository.uploadFile(file, keyPrefix)
                } else {
                    s3UploadRepository.uploadFile(file)
                }
                
                uploadResult
                    .onSuccess { s3Url ->
                        Log.i(TAG, "Successfully uploaded: ${file.name} to $s3Url")
                        removeFile(file)
                        // アップロード成功後のファイル削除ポリシーの統一
                        // 現在は画像も音声もローカルファイルを保持
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to upload: ${file.name}", error)
                        // ファイルはキューに残しておく（次回WiFi接続時に再試行）
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file: ${file.name}", e)
            }
        }
    }

    override suspend fun clearQueue(): Unit = withContext(Dispatchers.IO) {
        pendingUploads.clear()
        saveQueueToFile()
        Log.i(TAG, "Upload queue cleared")
    }

    private fun loadQueueFromFile() {
        try {
            if (queueFile.exists()) {
                val lines = queueFile.readLines()
                lines.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        pendingUploads.add(file)
                    }
                }
                Log.d(TAG, "Loaded ${pendingUploads.size} files from queue")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading queue from file", e)
        }
    }

    private fun saveQueueToFile() {
        try {
            val paths = pendingUploads.map { it.absolutePath }
            queueFile.writeText(paths.joinToString("\n"))
            Log.v(TAG, "Queue saved to file: ${paths.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue to file", e)
        }
    }
}