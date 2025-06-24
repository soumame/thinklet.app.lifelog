package ai.fd.thinklet.library.lifelog.data.audio.impl

import ai.fd.thinklet.library.lifelog.data.audio.AudioProcessorRepository
import ai.fd.thinklet.library.lifelog.data.audio.Mp3Encoder
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

    override suspend fun processWavToMp3(wavFile: File, recordingStartTime: Long): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!wavFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("WAV file does not exist: ${wavFile.absolutePath}"))
            }

            // MP3ファイル名を生成（S3アップロード用）
            val mp3FileName = generateMp3FileName(recordingStartTime)
            val mp3File = File(wavFile.parent, mp3FileName)

            // WAVからM4A（AAC）への変換（64kbps）
            val conversionResult = Mp3Encoder.convertWavToMp3(wavFile, mp3File)
            
            if (conversionResult.isSuccess) {
                Log.i(TAG, "Successfully converted WAV to AAC (.mp3): ${mp3File.name} (${mp3File.length()} bytes)")
                
                // S3アップロード処理（S3設定が存在する場合のみ）
                if (s3UploadRepository.isConfigured()) {
                    if (networkRepository.isWifiConnected()) {
                        // WiFi接続中の場合は即座にアップロード
                        s3UploadRepository.uploadFile(mp3File, "audio")
                            .onSuccess { s3Url ->
                                Log.i(TAG, "Audio file uploaded to S3: $s3Url")
                                // 画像と同様に、アップロード成功後もファイルを保持
                                // （UploadQueueRepositoryがキューから削除を管理）
                            }
                            .onFailure { error ->
                                Log.w(TAG, "Failed to upload audio file to S3, adding to queue", error)
                                // アップロードに失敗した場合はキューに追加
                                uploadQueueRepository.enqueueFile(mp3File)
                            }
                    } else {
                        // WiFi未接続の場合はキューに追加
                        Log.d(TAG, "Not connected to WiFi, adding audio to upload queue")
                        uploadQueueRepository.enqueueFile(mp3File)
                    }
                } else {
                    Log.d(TAG, "S3 upload not configured - audio file saved locally: ${mp3File.name}")
                }
                
                // コールバック通知
                callback?.onAudioProcessed(mp3File)
                
                Result.success(mp3File)
            } else {
                // 変換失敗
                Result.failure(conversionResult.exceptionOrNull() ?: Exception("Failed to convert WAV to MP3"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing WAV to MP3", e)
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