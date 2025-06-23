package ai.fd.thinklet.library.lifelog.data.audio.impl

import ai.fd.thinklet.library.lifelog.data.audio.AudioProcessorRepository
import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class AudioProcessorRepositoryImpl @Inject constructor(
    private val context: Context,
    private val networkRepository: NetworkRepository,
    private val s3UploadRepository: S3UploadRepository,
    private val uploadQueueRepository: UploadQueueRepository
) : AudioProcessorRepository {

    companion object {
        private const val TAG = "AudioProcessorRepositoryImpl"
    }

    private var callback: AudioProcessorRepository.AudioProcessorCallback? = null

    override suspend fun processRawToMp3(rawFile: File, recordingStartTime: Long): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!rawFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("RAW file does not exist: ${rawFile.absolutePath}"))
            }

            // MP3ファイル名を生成
            val mp3FileName = generateMp3FileName(recordingStartTime)
            val mp3File = File(rawFile.parent, mp3FileName)

            // 現在はRAWファイルをMP3としてリネーム（実際のMP3変換は実装が複雑なため）
            // 実際のプロダクションではFFmpegやMediaMuxerを使用してMP3変換を行う
            if (rawFile.renameTo(mp3File)) {
                Log.i(TAG, "Audio file renamed to MP3 format: ${mp3File.name}")
                
                // S3アップロード処理
                if (s3UploadRepository.isConfigured()) {
                    if (networkRepository.isWifiConnected()) {
                        // WiFi接続中の場合は即座にアップロード
                        s3UploadRepository.uploadFile(mp3File, "audio")
                            .onSuccess { s3Url ->
                                Log.i(TAG, "Audio file uploaded to S3: $s3Url")
                            }
                            .onFailure { error ->
                                Log.w(TAG, "Failed to upload audio file to S3", error)
                                // 失敗した場合はキューに追加
                                uploadQueueRepository.enqueueFile(mp3File)
                            }
                    } else {
                        // WiFi未接続の場合はキューに追加
                        uploadQueueRepository.enqueueFile(mp3File)
                        Log.d(TAG, "Audio file queued for upload: ${mp3File.name}")
                    }
                }
                
                // コールバック通知
                callback?.onAudioProcessed(mp3File)
                
                Result.success(mp3File)
            } else {
                Result.failure(Exception("Failed to rename RAW file to MP3"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing RAW to MP3", e)
            Result.failure(e)
        }
    }

    override fun savedEvent(callback: AudioProcessorRepository.AudioProcessorCallback) {
        this.callback = callback
    }

    override fun generateMp3FileName(recordingStartTime: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(recordingStartTime))
        return "$formattedTime.mp3"
    }
}