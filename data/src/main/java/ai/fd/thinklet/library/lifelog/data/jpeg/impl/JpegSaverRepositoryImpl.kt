package ai.fd.thinklet.library.lifelog.data.jpeg.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverCallback
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverRepository
import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import android.graphics.Bitmap
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class JpegSaverRepositoryImpl @Inject constructor(
    private val fileSelectorRepository: FileSelectorRepository,
    private val s3UploadRepository: S3UploadRepository,
    private val networkRepository: NetworkRepository,
    private val uploadQueueRepository: UploadQueueRepository
) : JpegSaverRepository {

    companion object {
        private const val TAG = "JpegSaverRepositoryImpl"
        private const val DATE_FORMAT = "yyyy-MM-dd-HHmmss"
        private const val FILE_EXTENSION = ".jpg"
    }

    private var savedCallback: JpegSaverCallback? = null

    override suspend fun saveJpeg(bitmap: Bitmap, quality: Int): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = createJpegFile()
            
            // JPEGファイルを保存
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.flush()
            }
            
            // EXIFメタデータを追加
            addExifMetadata(file)
            
            Log.i(TAG, "JPEG saved successfully: ${file.absolutePath}")
            
            // S3アップロード（設定されている場合）
            if (s3UploadRepository.isConfigured()) {
                if (networkRepository.isWifiConnected()) {
                    // WiFi接続中の場合は即座にアップロード
                    s3UploadRepository.uploadFile(file)
                        .onSuccess { s3Url ->
                            Log.i(TAG, "JPEG uploaded to S3: $s3Url")
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Failed to upload JPEG to S3, adding to queue", error)
                            // アップロードに失敗した場合はキューに追加
                            uploadQueueRepository.enqueueFile(file)
                        }
                } else {
                    // WiFi未接続の場合はキューに追加
                    Log.d(TAG, "Not connected to WiFi, adding JPEG to upload queue")
                    uploadQueueRepository.enqueueFile(file)
                }
            }
            
            // コールバックを実行
            savedCallback?.invoke(file)
            
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save JPEG", e)
            Result.failure(e)
        }
    }

    override fun savedEvent(callback: JpegSaverCallback) {
        this.savedCallback = callback
    }

    /**
     * YYYY-MM-DD-HHMMSS.jpg形式のファイル名でJPEGファイルを作成
     */
    private fun createJpegFile(): File {
        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val filename = "$timestamp$FILE_EXTENSION"
        
        // FileSelectorRepositoryの既存ロジックを利用してディレクトリを取得
        val gifFile = fileSelectorRepository.gifPath() ?: throw IllegalStateException("Failed to get gif path")
        val parentDir = gifFile.parentFile ?: throw IllegalStateException("Failed to get parent directory")
        
        // 新しいJPEGファイルを作成
        val jpegFile = File(parentDir, filename)
        
        // ディレクトリが存在しない場合は作成
        parentDir.mkdirs()
        
        return jpegFile
    }

    /**
     * JPEGファイルにEXIFメタデータを追加
     */
    private fun addExifMetadata(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            
            // 撮影日時を設定（現在時刻）
            val dateTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date())
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
            
            // カメラ情報を設定
            exif.setAttribute(ExifInterface.TAG_MAKE, "THINKLET")
            exif.setAttribute(ExifInterface.TAG_MODEL, "LifeLog Camera")
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "LifeLog v1.0")
            
            // 画像設定情報
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, ExifInterface.COLOR_SPACE_S_RGB.toString())
            
            // GPS情報（今回は設定しないが、将来的に位置情報を追加可能）
            // exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitude)
            // exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitude)
            
            exif.saveAttributes()
            Log.d(TAG, "EXIF metadata added to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add EXIF metadata", e)
            // EXIFの追加に失敗してもファイル保存は成功とみなす
        }
    }
}