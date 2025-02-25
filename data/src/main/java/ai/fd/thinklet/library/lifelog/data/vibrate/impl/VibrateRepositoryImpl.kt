package ai.fd.thinklet.library.lifelog.data.vibrate.impl

import ai.fd.thinklet.library.lifelog.data.vibrate.VibrateRepository
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import javax.inject.Inject

internal class VibrateRepositoryImpl @Inject constructor(context: Context) : VibrateRepository {
    @SuppressLint("ServiceCast")
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    override fun vibrate(effect: VibrationEffect) {
        vibrator.vibrate(effect)
    }
}
