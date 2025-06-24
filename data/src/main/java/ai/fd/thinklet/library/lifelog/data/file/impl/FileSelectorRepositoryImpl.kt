package ai.fd.thinklet.library.lifelog.data.file.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
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
    
    init {
        // 起動時に一時ファイルをクリーンアップ
        cleanupTempFiles()
    }
    
    fun setStoragePath(path: String?) {
        customStoragePath = path
    }
    override fun audioPath(): File? {
        return File(dir(), "${fileFormat()}.raw")
    }
    
    override fun wavPath(): File? {
        return File(dir(), "${fileFormat()}.wav")
    }
    
    override fun tempWavPath(): File? {
        // 録音中は単一のtemp.wavファイルを使用
        return File(dir(), "temp.wav")
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
            // カスタムパスが/DCIMで終わらない場合は、DCIM/lifelogとして扱う
            val customDir = if (customStoragePath!!.endsWith("/DCIM", true) || 
                              customStoragePath!!.endsWith("\\DCIM", true)) {
                File(customStoragePath!!, DIR)
            } else {
                File(File(customStoragePath!!, "DCIM"), DIR)
            }
            
            if (tryCreateDirectory(customDir)) {
                Log.i(TAG, "Using custom storage path: ${customDir.absolutePath}")
                return customDir
            } else {
                Log.w(TAG, "Failed to use custom storage path: ${customDir.absolutePath}, falling back to default")
            }
        }
        
        // SDカードを試行
        val sdCardDir = findSdCardPath()
        if (sdCardDir != null) {
            // DCIMディレクトリを含めたパスを作成
            val dcimDir = File(sdCardDir, "DCIM")
            val sdDir = File(dcimDir, DIR)
            
            // DCIMディレクトリが存在しない場合も作成を試みる
            if (!dcimDir.exists()) {
                Log.d(TAG, "DCIM directory not found on SD card, attempting to create: ${dcimDir.absolutePath}")
            }
            
            if (tryCreateDirectory(sdDir)) {
                Log.i(TAG, "Using SD card storage: ${sdDir.absolutePath}")
                return sdDir
            } else {
                Log.w(TAG, "Failed to use SD card storage: ${sdDir.absolutePath}")
            }
        }
        
        // デフォルトパスを使用
        val defaultDir = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && 
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            val dcim = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DCIM)
            File(dcim, DIR)
        } else {
            // Android 10以降またはパーミッションがない場合
            File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), DIR)
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
            // 親ディレクトリも含めて確実に作成
            if (!dir.exists()) {
                // 親ディレクトリ（DCIMなど）も存在しない場合は作成
                val parent = dir.parentFile
                if (parent != null && !parent.exists()) {
                    val parentCreated = parent.mkdirs()
                    Log.d(TAG, "Created parent directory: ${parent.absolutePath}, success: $parentCreated")
                }
                
                // 目的のディレクトリを作成
                val created = dir.mkdirs()
                Log.d(TAG, "Created directory: ${dir.absolutePath}, success: $created")
                
                // 作成後、書き込み可能かチェック
                if (created || dir.exists()) {
                    // テストファイルを作成して書き込み権限を確認
                    val testFile = File(dir, ".test_write_${System.currentTimeMillis()}")
                    try {
                        if (testFile.createNewFile()) {
                            testFile.delete()
                            Log.d(TAG, "Directory is writable: ${dir.absolutePath}")
                            return true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot write to directory: ${dir.absolutePath}", e)
                    }
                }
                false
            } else {
                // ディレクトリが既に存在する場合、書き込み可能かチェック
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

    /**
     * SDカードのパスを探す
     */
    private fun findSdCardPath(): String? {
        try {
            // 環境変数から外部ストレージディレクトリを取得
            val externalStorages = System.getenv("SECONDARY_STORAGE")
            if (!externalStorages.isNullOrEmpty()) {
                val paths = externalStorages.split(":")
                for (path in paths) {
                    val file = File(path)
                    if (file.exists() && file.canWrite()) {
                        Log.d(TAG, "Found writable SD card path from SECONDARY_STORAGE: $path")
                        return path
                    }
                }
            }
            
            // StorageManagerを使用してSDカードを探す（API 24以上）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val volumes = storageManager.storageVolumes
                for (volume in volumes) {
                    if (volume.isRemovable && !volume.isPrimary) {
                        val volumePath = getVolumePath(volume)
                        if (volumePath != null && File(volumePath).canWrite()) {
                            Log.d(TAG, "Found writable SD card via StorageManager: $volumePath")
                            return volumePath
                        }
                    }
                }
            }
            
            // 一般的なSDカードのパスを試行
            val possiblePaths = listOf(
                "/storage/sdcard1",
                "/storage/extSdCard",
                "/storage/external_SD",
                "/mnt/extSdCard",
                "/mnt/sdcard2",
                "/mnt/external_sd",
                "/mnt/media_rw/sdcard1"
            )
            
            for (path in possiblePaths) {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    Log.d(TAG, "Found writable SD card at common path: $path")
                    return path
                }
            }
            
            // /storageディレクトリ下のマウントポイントを探す
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val files = storageDir.listFiles() ?: emptyArray()
                for (file in files) {
                    // self, emulatedを除外
                    if (file.name != "self" && file.name != "emulated" && 
                        file.isDirectory && file.canWrite()) {
                        Log.d(TAG, "Found potential SD card: ${file.absolutePath}")
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding SD card path", e)
        }
        
        return null
    }
    
    /**
     * StorageVolumeからパスを取得（リフレクションを使用）
     */
    private fun getVolumePath(volume: StorageVolume): String? {
        return try {
            val getPath = volume.javaClass.getMethod("getPath")
            getPath.invoke(volume) as? String
        } catch (e: Exception) {
            Log.e(TAG, "Error getting volume path", e)
            null
        }
    }
    
    /**
     * 一時ファイルをクリーンアップ
     */
    private fun cleanupTempFiles() {
        try {
            val directory = rootDir()
            if (directory.exists() && directory.isDirectory) {
                val tempFile = File(directory, "temp.wav")
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    Log.d(TAG, "Cleaned up temp.wav file, success: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during temp file cleanup", e)
        }
    }
    
    private companion object {
        const val DIR = "lifelog"
        const val TAG = "FileSelectorRepository"
    }
}
