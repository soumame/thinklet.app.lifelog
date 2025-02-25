package ai.fd.thinklet.library.lifelog.data.gif

import android.graphics.Bitmap
import java.io.File

/**
 * Gifエンコーダ
 */
interface GifEncoderRepository {
    /**
     * GifEncoderRepository のコールバック
     */
    fun interface GifEncoderCallback {
        /**
         * 保存完了したファイルのコールバック．
         */
        fun onSavedFile(file: File)
    }

    /**
     * フレームの追加
     */
    fun pushFrame(bitmap: Bitmap, interval: Long = 100L)

    /**
     * コールバック関数の追加
     */
    fun savedEvent(callback: GifEncoderCallback)

    /**
     * 書き込んでいるGifを即座に終了する
     */
    fun close()
}
