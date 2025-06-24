package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.library.lifelog.data.audio.AudioProcessorRepository
import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.mic.MicRepository
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * マイクからの音声データを録音するUseCase
 */
class MicRecordUseCase @Inject constructor(
    private val micRepository: MicRepository,
    private val audioCaptureRepository: AudioCaptureRepository,
    private val fileSelectorRepository: FileSelectorRepository,
    private val audioProcessorRepository: AudioProcessorRepository
) {
    init {
        audioCaptureRepository.savedEvent { tempWavFile, recordingStartTime ->
            Log.i(TAG, "10-minute recording period completed: ${tempWavFile.absolutePath}, started at: $recordingStartTime")
            
            // 10分間録音されたtemp.wavをMP3に変換してS3にアップロード
            GlobalScope.launch {
                audioProcessorRepository.processWavToMp3(tempWavFile, recordingStartTime)
                    .onSuccess { mp3File ->
                        Log.i(TAG, "Audio converted to MP3 for S3 upload: ${mp3File.name}")
                        
                        // 注意: temp.wavファイルは次の録音セッションで再利用されるため、
                        // ここでは削除せず、次のローテーション時に上書きされる
                        Log.d(TAG, "temp.wav will be reused for next recording session")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to convert temp.wav to MP3", error)
                        // 変換失敗時でもtemp.wavは保持（次の録音で再利用）
                    }
            }
        }
    }

    suspend operator fun invoke() {
        coroutineScope {
            try {
                micRecord()
            } finally {
                Log.d(TAG, "Stop micRecord")
                audioCaptureRepository.close()
            }
        }
    }

    private suspend fun CoroutineScope.micRecord() {
        micRepository.startRecording().collect {
            it.onSuccess {
                Log.v(TAG, "micRecord success")
                audioCaptureRepository.pushPcm(it)
            }.onFailure {
                Log.e(TAG, "Failed to micRecord", it)
            }
        }
    }

    private companion object {
        const val TAG = "MicRecordUseCase"
    }
}
