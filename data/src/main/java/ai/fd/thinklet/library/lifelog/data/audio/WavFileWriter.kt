package ai.fd.thinklet.library.lifelog.data.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAVファイルの書き込みを行うクラス
 */
class WavFileWriter(
    private val file: File,
    private val sampleRate: Int = 44100,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private var dataSize: Long = 0
    private val headerSize = 44
    
    init {
        // WAVヘッダーを書き込む（データサイズは後で更新）
        writeWavHeader()
    }
    
    /**
     * PCMデータを追加
     */
    fun appendPcmData(pcmData: ByteArray) {
        file.appendBytes(pcmData)
        dataSize += pcmData.size
    }
    
    /**
     * WAVファイルを完成させる（ヘッダーのサイズ情報を更新）
     */
    fun finalize() {
        updateWavHeader()
    }
    
    /**
     * 現在のファイルサイズを取得
     */
    fun getFileSize(): Long = file.length()
    
    private fun writeWavHeader() {
        val header = ByteArray(headerSize)
        val buffer = ByteBuffer.wrap(header)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(0) // File size - 8 (will be updated later)
        buffer.put("WAVE".toByteArray())
        
        // fmt subchunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1) // AudioFormat (1 for PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bitsPerSample / 8) // ByteRate
        buffer.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        buffer.putShort(bitsPerSample.toShort())
        
        // data subchunk
        buffer.put("data".toByteArray())
        buffer.putInt(0) // Subchunk2Size (will be updated later)
        
        file.writeBytes(header)
    }
    
    private fun updateWavHeader() {
        RandomAccessFile(file, "rw").use { raf ->
            // Update file size
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes((dataSize + headerSize - 8).toInt()))
            
            // Update data size
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(dataSize.toInt()))
        }
    }
}