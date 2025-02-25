package ai.fd.thinklet.library.lifelog.data.audioCapture

import java.io.File

/**
 * 録音したデータを処理するリポジトリ
 */
interface AudioCaptureRepository {
    /**
     * 保存先ファイル変更のイベント
     */
    fun interface AudioCaptureCallback {
        fun onSavedFile(file: File)
    }

    /**
     * PCMデータ [data] をPushする
     *
     */
    fun pushPcm(data: ByteArray)

    /**
     * コールバックを設定する
     */
    fun savedEvent(callback: AudioCaptureCallback)

    /**
     * 書き込み中のファイルをCloseする．
     */
    fun close()
}
