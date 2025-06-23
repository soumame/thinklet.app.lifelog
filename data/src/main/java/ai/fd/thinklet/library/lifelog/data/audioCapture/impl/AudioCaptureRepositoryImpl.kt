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

    override fun pushPcm(data: ByteArray) {
        if (file?.isFileSizeLimited() == true) {
            file?.also { callback?.onSavedFile(it) }
            file = fileSelectorRepository.audioPath()
            recordingStartTime = System.currentTimeMillis()
        } else if (file == null) {
            file = fileSelectorRepository.audioPath()
            recordingStartTime = System.currentTimeMillis()
        }
        file?.appendBytes(data)
    }

    override fun savedEvent(callback: AudioCaptureRepository.AudioCaptureCallback) {
        this.callback = callback
    }

    override fun close() {
        file?.also { callback?.onSavedFile(it) }
        file = null
    }

    private companion object {
        const val TAG = "AudioCaptureRepository"
        const val LIMIT = 1L * 1000 * 1000 * 1000
        fun File.isFileSizeLimited(): Boolean {
            return this.exists() && this.isFile && this.length() >= LIMIT
        }
    }
}
