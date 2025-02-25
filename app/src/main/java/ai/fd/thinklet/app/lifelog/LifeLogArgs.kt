package ai.fd.thinklet.app.lifelog

import android.os.Bundle
import android.util.Size
import kotlin.math.max

data class LifeLogArgs(
    val longSide: Int,
    val shortSide: Int,
    val intervalSeconds: Int,
    val enabledMic: Boolean
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
        )
    }
}
