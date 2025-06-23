package ai.fd.thinklet.library.lifelog.data.audio

import java.io.File

/**
 * 音声ファイル処理のリポジトリ
 */
interface AudioProcessorRepository {
    /**
     * 音声ファイル保存完了時のコールバック
     */
    fun interface AudioProcessorCallback {
        fun onAudioProcessed(mp3File: File)
    }

    /**
     * RAWファイルをMP3に変換してS3にアップロード
     */
    suspend fun processRawToMp3(rawFile: File, recordingStartTime: Long): Result<File>

    /**
     * 保存完了のコールバックを設定
     */
    fun savedEvent(callback: AudioProcessorCallback)

    /**
     * 録音開始時刻に基づいてMP3ファイル名を生成
     */
    fun generateMp3FileName(recordingStartTime: Long): String
}