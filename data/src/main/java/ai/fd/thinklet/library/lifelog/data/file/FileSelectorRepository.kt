package ai.fd.thinklet.library.lifelog.data.file

import java.io.File

/**
 * 書き込み先ファイルを管理するリポジトリ
 */
interface FileSelectorRepository {

    /**
     * 音声ファイルのパスを取得する
     */
    fun audioPath(): File?
    
    /**
     * WAVファイルのパスを取得する
     */
    fun wavPath(): File?
    
    /**
     * 一時WAVファイルのパスを取得する
     */
    fun tempWavPath(): File?

    /**
     * 画像(GIF)ファイルのパスを取得する
     */
    fun gifPath(): File?

    /**
     * 処理が完了した[file]を処理する．
     * ここでは，MediaScannerにかけて，MTPに反映させる．
     */
    fun deploy(file: File): Boolean
}
