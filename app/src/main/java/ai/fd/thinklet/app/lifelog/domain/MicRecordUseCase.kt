package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.mic.MicRepository
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * マイクからの音声データを録音するUseCase
 */
class MicRecordUseCase @Inject constructor(
    private val micRepository: MicRepository,
    private val audioCaptureRepository: AudioCaptureRepository,
    private val fileSelectorRepository: FileSelectorRepository
) {
    init {
        audioCaptureRepository.savedEvent {
            Log.i(TAG, "savedEvent mic: ${it.absoluteFile}")
            fileSelectorRepository.deploy(it)
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
