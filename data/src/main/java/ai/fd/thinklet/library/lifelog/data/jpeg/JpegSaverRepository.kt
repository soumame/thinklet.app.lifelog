package ai.fd.thinklet.library.lifelog.data.jpeg

import android.graphics.Bitmap
import java.io.File

typealias JpegSaverCallback = (File) -> Unit

interface JpegSaverRepository {
    /**
     * Bitmapを指定された品質でJPEGファイルとして保存します
     * @param bitmap 保存するBitmap
     * @param quality JPEG圧縮品質 (0-100)
     * @return 保存に成功した場合はFile、失敗した場合はException
     */
    suspend fun saveJpeg(bitmap: Bitmap, quality: Int = 90): Result<File>
    
    /**
     * JPEG保存完了時のコールバックを設定します
     * @param callback 保存完了時に呼び出されるコールバック
     */
    fun savedEvent(callback: JpegSaverCallback)
}