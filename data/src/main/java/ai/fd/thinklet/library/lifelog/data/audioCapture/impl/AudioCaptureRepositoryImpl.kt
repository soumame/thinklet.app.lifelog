package ai.fd.thinklet.library.lifelog.data.audioCapture.impl

import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import java.io.File
import javax.inject.Inject

internal class AudioCaptureRepositoryImpl @Inject constructor(
    private val fileSelectorRepository: FileSelectorRepository
) : AudioCaptureRepository {
    private var callback: AudioCaptureRepository.AudioCaptureCallback? = null
    private var file: File? = null
    private var recordingStartTime: Long = 0
    private var currentFileStartTime: Long = 0

    override fun pushPcm(data: ByteArray) {
        val currentTime = System.currentTimeMillis()
        
        // Check if we need to create a new file (size limit or time limit)
        if (file?.shouldRotate(currentTime - currentFileStartTime) == true) {
            file?.also { callback?.onSavedFile(it, currentFileStartTime) }
            file = fileSelectorRepository.audioPath()
            currentFileStartTime = currentTime
            if (recordingStartTime == 0L) {
                recordingStartTime = currentTime
            }
        } else if (file == null) {
            file = fileSelectorRepository.audioPath()
            recordingStartTime = currentTime
            currentFileStartTime = currentTime
        }
        file?.appendBytes(data)
    }

    override fun savedEvent(callback: AudioCaptureRepository.AudioCaptureCallback) {
        this.callback = callback
    }

    override fun close() {
        file?.also { callback?.onSavedFile(it, currentFileStartTime) }
        file = null
    }

    private companion object {
        const val TAG = "AudioCaptureRepository"
        // 10MB limit for audio files
        const val SIZE_LIMIT = 10L * 1024 * 1024  // 10MB
        // 10 minutes time limit
        const val TIME_LIMIT_MS = 10L * 60 * 1000  // 10 minutes in milliseconds
        
        fun File.shouldRotate(elapsedTimeMs: Long): Boolean {
            // Rotate if file size exceeds 10MB OR recording time exceeds 10 minutes
            val sizeExceeded = this.exists() && this.isFile && this.length() >= SIZE_LIMIT
            val timeExceeded = elapsedTimeMs >= TIME_LIMIT_MS
            return sizeExceeded || timeExceeded
        }
    }
}
