package ai.fd.thinklet.library.lifelog.data.file.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class FileSelectorRepositoryImpl @Inject constructor(
    private val context: Context
) : FileSelectorRepository {
    
    private var customStoragePath: String? = null
    
    fun setStoragePath(path: String?) {
        customStoragePath = path
    }
    override fun audioPath(): File? {
        return File(dir(), "${fileFormat()}.raw")
    }

    override fun gifPath(): File? {
        return File(dir(), "${fileFormat()}.gif")
    }

    override fun deploy(file: File): Boolean {
        return updateIndex(file)
    }

    private fun fileFormat(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun dir(): File {
        return File(
            rootDir(),
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        ).apply {
            mkdirs()
        }
    }

    private fun rootDir(): File {
        // カスタムストレージパスを試行
        if (!customStoragePath.isNullOrEmpty()) {
            val customDir = File(customStoragePath!!, DIR)
            if (tryCreateDirectory(customDir)) {
                Log.i(TAG, "Using custom storage path: ${customDir.absolutePath}")
                return customDir
            } else {
                Log.w(TAG, "Failed to use custom storage path: ${customDir.absolutePath}, falling back to default")
            }
        }
        
        // デフォルトパスを使用
        val defaultDir = if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            val download = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DCIM)
            File(download, DIR)
        } else {
            File(context.getExternalFilesDir(null), DIR)
        }
        
        if (tryCreateDirectory(defaultDir)) {
            Log.i(TAG, "Using default storage path: ${defaultDir.absolutePath}")
            return defaultDir
        } else {
            // 最終フォールバック: アプリ内部ストレージ
            val internalDir = File(context.getExternalFilesDir(null), DIR)
            tryCreateDirectory(internalDir)
            Log.i(TAG, "Using internal storage fallback: ${internalDir.absolutePath}")
            return internalDir
        }
    }
    
    private fun tryCreateDirectory(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "Created directory: ${dir.absolutePath}, success: $created")
                created && dir.exists() && dir.canWrite()
            } else {
                dir.canWrite()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory: ${dir.absolutePath}", e)
            false
        }
    }

    private fun updateIndex(file: File): Boolean {
        Log.d(TAG, "handleCompletedFile")
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            object : MediaScannerConnection.MediaScannerConnectionClient {
                override fun onScanCompleted(path: String, uri: Uri) {
                    Log.d(TAG, "onScanCompleted path:$path, uri:$uri")
                }

                override fun onMediaScannerConnected() {
                    Log.v(TAG, "onMediaScannerConnected")
                }
            })
        Log.d(TAG, "success handleCompletedFile $file")
        return true
    }

    private companion object {
        const val DIR = "lifelog"
        const val TAG = "FileSelectorRepository"
    }
}
