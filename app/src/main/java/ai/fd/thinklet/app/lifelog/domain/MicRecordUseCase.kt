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
        audioCaptureRepository.savedEvent { rawFile ->
            Log.i(TAG, "savedEvent mic: ${rawFile.absoluteFile}")
            fileSelectorRepository.deploy(rawFile)
            
            // RAWファイルをMP3に変換してS3にアップロード
            GlobalScope.launch {
                val recordingStartTime = System.currentTimeMillis() // 実際は録音開始時刻を取得
                audioProcessorRepository.processRawToMp3(rawFile, recordingStartTime)
                    .onSuccess { mp3File ->
                        Log.i(TAG, "Audio converted to MP3: ${mp3File.name}")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to convert audio to MP3", error)
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
