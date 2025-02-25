package ai.fd.thinklet.library.lifelog.data.vibrate

import android.os.VibrationEffect

/**
 * バイブレーションレポジトリ
 */
interface VibrateRepository {
    /**
     * バイブレーションを実行する
     */
    fun vibrate(effect: VibrationEffect)

    companion object {
        val SHOOT: VibrationEffect
            get() = VibrationEffect.createWaveform(
                longArrayOf(0L, 50L),
                intArrayOf(
                    0,
                    VibrationEffect.DEFAULT_AMPLITUDE
                ),
                -1
            )
    }
}
