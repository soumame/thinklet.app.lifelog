package ai.fd.thinklet.library.lifelog.data.snapshot

import android.graphics.Bitmap
import android.util.Size

/**
 * 写真撮影リポジトリ
 */
interface SnapShotRepository {
    /**
     * 撮影を開始する
     * @param size 撮影サイズ, nullの場合はデフォルトサイズ
     * @return 撮影した写真
     */
    suspend fun takePhoto(size: Size? = null): Result<Bitmap>
}
