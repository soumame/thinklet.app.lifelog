package ai.fd.thinklet.library.lifelog.data.audioCapture.impl

import ai.fd.thinklet.library.lifelog.data.audio.WavFileWriter
import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import android.util.Log
import java.io.File
import javax.inject.Inject

internal class AudioCaptureRepositoryImpl @Inject constructor(
    private val fileSelectorRepository: FileSelectorRepository
) : AudioCaptureRepository {
    private var callback: AudioCaptureRepository.AudioCaptureCallback? = null
    private var file: File? = null
    private var wavWriter: WavFileWriter? = null
    private var recordingStartTime: Long = 0
    private var currentFileStartTime: Long = 0

    override fun pushPcm(data: ByteArray) {
        val currentTime = System.currentTimeMillis()
        
        // Check if we need to rotate (10 minutes time limit)
        if (wavWriter != null && shouldRotate(currentTime - currentFileStartTime)) {
            // Finalize current temp.wav file
            wavWriter?.finalize()
            file?.also { tempFile ->
                Log.i(TAG, "Temp WAV file completed: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
                // 一時ファイルなのでMediaScannerには登録しない
                callback?.onSavedFile(tempFile, currentFileStartTime) 
            }
            
            // Reset for next recording period - reuse same temp.wav file
            file = fileSelectorRepository.tempWavPath()
            file?.let {
                // Delete existing temp.wav if it exists
                if (it.exists()) {
                    it.delete()
                }
                wavWriter = WavFileWriter(it)
                currentFileStartTime = currentTime
            }
        } else if (file == null || wavWriter == null) {
            // Initialize first temp.wav file
            file = fileSelectorRepository.tempWavPath()
            file?.let {
                // Delete existing temp.wav if it exists
                if (it.exists()) {
                    it.delete()
                }
                wavWriter = WavFileWriter(it)
                recordingStartTime = currentTime
                currentFileStartTime = currentTime
            }
        }
        
        // Write PCM data to WAV file
        wavWriter?.appendPcmData(data)
    }

    override fun savedEvent(callback: AudioCaptureRepository.AudioCaptureCallback) {
        this.callback = callback
    }

    override fun close() {
        // Finalize WAV file
        wavWriter?.finalize()
        file?.also { tempFile ->
            Log.i(TAG, "Temp WAV file closed: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            callback?.onSavedFile(tempFile, currentFileStartTime) 
        }
        wavWriter = null
        file = null
    }

    private fun shouldRotate(elapsedTimeMs: Long): Boolean {
        // Rotate only based on time (10 minutes), not file size
        return elapsedTimeMs >= TIME_LIMIT_MS
    }
    
    private companion object {
        const val TAG = "AudioCaptureRepository"
        // 10 minutes time limit for audio files
        const val TIME_LIMIT_MS = 10L * 60 * 1000  // 10 minutes in milliseconds
    }
}
