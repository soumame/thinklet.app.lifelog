package ai.fd.thinklet.app.lifelog

import android.os.Bundle
import android.util.Size
import kotlin.math.max

data class LifeLogArgs(
    val longSide: Int,
    val shortSide: Int,
    val intervalSeconds: Int,
    val enabledMic: Boolean,
    val s3BucketName: String?,
    val s3Region: String?,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
    val s3Endpoint: String?,
    val s3Enabled: Boolean,
    val storagePath: String?
) {
    val size: Size
        get() = Size(longSide, shortSide)

    companion object {
        @Suppress("DEPRECATION")
        fun get(bundle: Bundle?): LifeLogArgs = LifeLogArgs(
            longSide = bundle?.get("longSide")?.toString()?.toIntOrNull() ?: 640,
            shortSide = bundle?.get("shortSide")?.toString()?.toIntOrNull() ?: 480,
            intervalSeconds = max(bundle?.get("intervalSeconds")?.toString()?.toIntOrNull() ?: 300, 10), // 最小で10秒
            enabledMic = bundle?.get("enabledMic")?.toString()?.toBooleanStrictOrNull() == true,
            s3BucketName = bundle?.get("s3BucketName")?.toString(),
            s3Region = bundle?.get("s3Region")?.toString(),
            s3AccessKey = bundle?.get("s3AccessKey")?.toString(),
            s3SecretKey = bundle?.get("s3SecretKey")?.toString(),
            s3Endpoint = bundle?.get("s3Endpoint")?.toString(),
            s3Enabled = bundle?.get("s3Enabled")?.toString()?.toBooleanStrictOrNull() == true,
            storagePath = bundle?.get("storagePath")?.toString(),
        )
    }
}
