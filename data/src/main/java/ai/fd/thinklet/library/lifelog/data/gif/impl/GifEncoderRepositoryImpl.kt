package ai.fd.thinklet.library.lifelog.data.gif.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.gif.GifEncoderRepository
import ai.fd.thinklet.library.lifelog.data.gif.GifEncoderRepository.GifEncoderCallback
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.annotation.GuardedBy
import com.waynejo.androidndkgif.GifEncoder
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock

internal class GifEncoderRepositoryImpl @Inject constructor(
    private val fileSelectorRepository: FileSelectorRepository,
    private val maxFramePerFile: Long = LIMIT
) : GifEncoderRepository {

    @GuardedBy("gitEncoderLock")
    private var gifEncoder: GifEncoderWrapper? = null
    private val gitEncoderLock = ReentrantLock()

    private var callback: GifEncoderCallback? = null

    override fun pushFrame(bitmap: Bitmap, interval: Long) {
        if (!prepare(bitmap)) return
        kotlin.runCatching {
            pushFrameInner(bitmap, interval)
        }.onFailure {
            Log.e(TAG, "Failed to pushFrame", it)
        }
    }

    override fun savedEvent(callback: GifEncoderCallback) {
        this.callback = callback
    }

    override fun close() {
        gitEncoderLock.withLock {
            gifEncoder?.also {
                it.close()
                callback?.onSavedFile(it.file)
            }
            gifEncoder = null
        }
    }

    private fun prepare(bitmap: Bitmap): Boolean = gitEncoderLock.withLock {
        val width = bitmap.width
        val height = bitmap.height

        if (gifEncoder != null) {
            val gifEnc = gifEncoder!!
            // 1. 上限フレームに到達したか？
            if (gifEnc.getFrameCount() >= maxFramePerFile) {
                close()
                val file = fileSelectorRepository.gifPath() ?: return false
                gifEncoder = GifEncoderWrapper(file, Size(width, height))
                return true
            }
            // 2. Gifのサイズチェック．
            if (gifEnc.size.width == width && gifEnc.size.height == height) {
                // 画像の大きさが同じなら許可．
                return true
            }
            close()
        }

        // 0. エンコーダーのセットアップ
        val file = fileSelectorRepository.gifPath() ?: return false
        gifEncoder = GifEncoderWrapper(file, Size(width, height))
        return true
    }

    private fun pushFrameInner(bitmap: Bitmap, interval: Long) = gitEncoderLock.withLock {
        gifEncoder?.encodeFrame(bitmap, interval)
    }

    private class GifEncoderWrapper(
        val file: File,
        val size: Size
    ) {
        private val gifEncoder = GifEncoder()
        private var frameCount: Long = 0L

        init {
            gifEncoder.init(
                size.width,
                size.height,
                file.path,
                GifEncoder.EncodingType.ENCODING_TYPE_SIMPLE_FAST
            )
        }

        fun getFrameCount(): Long = frameCount

        fun encodeFrame(bitmap: Bitmap, delayMs: Long): Boolean {
            val ret = gifEncoder.encodeFrame(bitmap, delayMs.toInt())
            if (ret) {
                frameCount++
                Log.v(TAG, "encodeFrame count=$frameCount")
            }
            return ret
        }

        fun close() {
            gifEncoder.close()
        }
    }

    private companion object {
        const val TAG = "GifEncoderRepository"
        const val LIMIT = 600L // 1分ごととして，60*10=600枚
    }
}
