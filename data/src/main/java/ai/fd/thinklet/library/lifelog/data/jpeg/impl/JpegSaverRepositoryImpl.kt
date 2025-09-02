package ai.fd.thinklet.library.lifelog.data.jpeg.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.http.HttpUploadRepository
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverCallback
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverRepository
import ai.fd.thinklet.library.lifelog.data.location.LocationRepository
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
import java.util.TimeZone
import javax.inject.Inject

class JpegSaverRepositoryImpl @Inject constructor(
    private val fileSelectorRepository: FileSelectorRepository,
    private val s3UploadRepository: S3UploadRepository,
    private val httpUploadRepository: HttpUploadRepository,
    private val networkRepository: NetworkRepository,
    private val uploadQueueRepository: UploadQueueRepository,
    private val locationRepository: LocationRepository
) : JpegSaverRepository {

    companion object {
        private const val TAG = "JpegSaverRepositoryImpl"
        private const val DATE_FORMAT = "yyyy-MM-dd-HHmmss"
        private const val FILE_EXTENSION = ".jpg"
        private const val MIN_LOCATION_ACCURACY = 50f // 位置情報の精度しきい値（メートル）
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
            
            // 位置情報を取得
            val location = locationRepository.getCurrentLocation().getOrNull()
            
            // EXIFメタデータを追加（位置情報を含む）
            addExifMetadata(file, location)
            
            Log.i(TAG, "JPEG saved successfully: ${file.absolutePath}")
            
            // HTTPアップロード（設定されている場合）
            if (httpUploadRepository.isConfigured()) {
                if (networkRepository.isWifiConnected()) {
                    // WiFi接続中の場合は即座にアップロード
                    httpUploadRepository.uploadFile(file)
                        .onSuccess { response ->
                            Log.i(TAG, "JPEG uploaded via HTTP: $response")
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Failed to upload JPEG via HTTP, adding to queue", error)
                            // アップロードに失敗した場合はキューに追加
                            uploadQueueRepository.enqueueFile(file)
                        }
                } else {
                    // WiFi未接続の場合はキューに追加
                    Log.d(TAG, "Not connected to WiFi, adding JPEG to upload queue")
                    uploadQueueRepository.enqueueFile(file)
                }
            }
            // S3アップロード（設定されている場合、HTTPが設定されていない場合のみ）
            else if (s3UploadRepository.isConfigured()) {
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
     * YYYY-MM-DD-HHMMSS.jpg形式のファイル名でJPEGファイルを作成（UTC時刻）
     */
    private fun createJpegFile(): File {
        val utcFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = utcFormat.format(Date())
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
    private fun addExifMetadata(file: File, location: android.location.Location?) {
        try {
            val exif = ExifInterface(file.absolutePath)
            
            // 現在時刻をUTCとローカルで取得
            val now = Date()
            
            // UTC時刻を設定
            val utcFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val utcDateTime = utcFormat.format(now)
            
            // 基本的な日時タグにはUTC時刻を設定（API要件に従う）
            exif.setAttribute(ExifInterface.TAG_DATETIME, utcDateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, utcDateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, utcDateTime)
            
            // オフセット時間を計算して設定（UTC基準なので+00:00）
            val offsetString = "+00:00"
            
            // オフセット時間タグを設定（EXIF 2.31以降）
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetString)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetString)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetString)
            
            // ローカルタイムゾーンのオフセットを別途保存（カスタムタグまたはユーザーコメントとして）
            val localOffsetMinutes = TimeZone.getDefault().getOffset(now.time) / 60000
            val localOffsetHours = localOffsetMinutes / 60
            val localOffsetMins = Math.abs(localOffsetMinutes % 60)
            val localOffsetString = String.format("%+03d:%02d", localOffsetHours, localOffsetMins)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "LocalTimezoneOffset:$localOffsetString")
            
            // カメラ情報を設定
            exif.setAttribute(ExifInterface.TAG_MAKE, "THINKLET")
            exif.setAttribute(ExifInterface.TAG_MODEL, "LifeLog Camera")
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "LifeLog v1.0")
            
            // 画像設定情報
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, ExifInterface.COLOR_SPACE_S_RGB.toString())
            
            // GPS情報を追加（精度が十分高い場合のみ）
            if (location != null && location.hasAccuracy() && location.accuracy <= MIN_LOCATION_ACCURACY) {
                // 緯度を度分秒形式に変換して設定
                val latRef = if (location.latitude >= 0) "N" else "S"
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(Math.abs(location.latitude)))
                
                // 経度を度分秒形式に変換して設定
                val lonRef = if (location.longitude >= 0) "E" else "W"
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(Math.abs(location.longitude)))
                
                // 高度情報（メートル単位）
                if (location.hasAltitude()) {
                    val altRef = if (location.altitude >= 0) 0 else 1
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef.toString())
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${Math.abs(location.altitude).toLong()}/1")
                }
                
                // GPS時刻をUTCで設定
                val gpsTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val gpsTime = gpsTimeFormat.format(Date(location.time))
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTime)
                
                // GPS日付をUTCで設定
                val gpsDateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val gpsDate = gpsDateFormat.format(Date(location.time))
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDate)
                
                Log.d(TAG, "GPS info added: lat=${location.latitude}, lon=${location.longitude}, alt=${location.altitude}, accuracy=${location.accuracy}m")
            } else if (location != null) {
                Log.d(TAG, "GPS info not added due to low accuracy: ${location.accuracy}m (threshold: ${MIN_LOCATION_ACCURACY}m)")
            }
            
            exif.saveAttributes()
            Log.d(TAG, "EXIF metadata added to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add EXIF metadata", e)
            // EXIFの追加に失敗してもファイル保存は成功とみなす
        }
    }
    
    /**
     * 十進度数を度分秒形式（DMS）に変換
     * @param decimal 十進度数
     * @return "度/1,分/1,秒/1000" 形式の文字列
     */
    private fun convertToDMS(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesDecimal = (decimal - degrees) * 60
        val minutes = minutesDecimal.toInt()
        val seconds = ((minutesDecimal - minutes) * 60 * 1000).toInt()  // 秒を1/1000精度で保存
        
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}