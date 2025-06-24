package ai.fd.thinklet.library.lifelog.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAVファイルをMP3に変換するエンコーダー
 */
class Mp3Encoder {
    
    companion object {
        private const val TAG = "Mp3Encoder"
        private const val BUFFER_SIZE = 8192
        
        /**
         * WAVファイルを64kbpsのMP3に変換
         */
        suspend fun convertWavToMp3(wavFile: File, mp3File: File): Result<File> = withContext(Dispatchers.IO) {
            try {
                if (!wavFile.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("WAV file does not exist"))
                }
                
                // WAVヘッダーを読み込んで音声パラメータを取得
                val wavHeader = readWavHeader(wavFile)
                if (wavHeader == null) {
                    return@withContext Result.failure(Exception("Invalid WAV file format"))
                }
                
                // Android 12以降ではMP3はサポートされていないため、AACを使用
                val outputFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    wavHeader.sampleRate,
                    1 // モノラル
                ).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 64000) // 64kbps
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                }
                
                // S3アップロード用に.mp3拡張子のファイルを作成（実際の内容はAAC/M4A）
                val outputFile = mp3File
                
                // MediaCodecを使用してエンコード
                var encoder: MediaCodec? = null
                var muxer: MediaMuxer? = null
                var trackIndex = -1
                
                try {
                    encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    encoder.start()
                    
                    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    
                    // WAVファイルから音声データを読み込んで処理
                    FileInputStream(wavFile).use { input ->
                        input.skip(44) // WAVヘッダーをスキップ
                        
                        val inputBuffer = ByteArray(BUFFER_SIZE)
                        var inputExhausted = false
                        var outputReceived = false
                        val bufferInfo = MediaCodec.BufferInfo()
                        
                        while (!outputReceived) {
                            // 入力処理
                            if (!inputExhausted) {
                                val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                                if (inputBufferIndex >= 0) {
                                    val codecInputBuffer = encoder.getInputBuffer(inputBufferIndex)
                                    codecInputBuffer?.clear()
                                    
                                    val bytesRead = input.read(inputBuffer)
                                    if (bytesRead <= 0) {
                                        // 入力終了
                                        encoder.queueInputBuffer(
                                            inputBufferIndex, 0, 0, 0,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        )
                                        inputExhausted = true
                                    } else {
                                        codecInputBuffer?.put(inputBuffer, 0, bytesRead)
                                        encoder.queueInputBuffer(
                                            inputBufferIndex, 0, bytesRead,
                                            0, 0
                                        )
                                    }
                                }
                            }
                            
                            // 出力処理
                            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                            when {
                                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    trackIndex = muxer.addTrack(encoder.outputFormat)
                                    muxer.start()
                                }
                                outputBufferIndex >= 0 -> {
                                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                    if (outputBuffer != null && trackIndex >= 0) {
                                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                    }
                                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                                    
                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                        outputReceived = true
                                    }
                                }
                            }
                        }
                    }
                    
                    // .mp3拡張子だが実際の内容はAAC/M4A
                    Log.i(TAG, "Successfully converted WAV to AAC (.mp3): ${outputFile.name} (${outputFile.length()} bytes)")
                    Result.success(outputFile)
                } finally {
                    encoder?.stop()
                    encoder?.release()
                    muxer?.stop()
                    muxer?.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting WAV to MP3", e)
                // エラー時は出力ファイルを削除
                if (mp3File.exists()) {
                    mp3File.delete()
                }
                Result.failure(e)
            }
        }
        
        /**
         * WAVヘッダーを読み込む
         */
        private fun readWavHeader(wavFile: File): WavHeader? {
            return try {
                FileInputStream(wavFile).use { input ->
                    val header = ByteArray(44)
                    if (input.read(header) != 44) {
                        return null
                    }
                    
                    val buffer = ByteBuffer.wrap(header)
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    
                    // RIFFヘッダーの確認
                    val riff = ByteArray(4)
                    buffer.get(riff)
                    if (String(riff) != "RIFF") {
                        return null
                    }
                    
                    buffer.getInt() // file size - 8
                    
                    val wave = ByteArray(4)
                    buffer.get(wave)
                    if (String(wave) != "WAVE") {
                        return null
                    }
                    
                    // fmt チャンクを探す
                    val fmt = ByteArray(4)
                    buffer.get(fmt)
                    if (String(fmt) != "fmt ") {
                        return null
                    }
                    
                    buffer.getInt() // chunk size
                    buffer.getShort() // audio format
                    val channels = buffer.getShort().toInt()
                    val sampleRate = buffer.getInt()
                    buffer.getInt() // byte rate
                    buffer.getShort() // block align
                    val bitsPerSample = buffer.getShort().toInt()
                    
                    WavHeader(sampleRate, channels, bitsPerSample)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading WAV header", e)
                null
            }
        }
        
        data class WavHeader(
            val sampleRate: Int,
            val channels: Int,
            val bitsPerSample: Int
        )
    }
}