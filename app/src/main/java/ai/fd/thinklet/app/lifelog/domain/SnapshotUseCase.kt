package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.gif.GifEncoderRepository
import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import android.util.Log
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * 定期的に写真撮影をして，GifエンコードにかけるUseCase
 */
class SnapshotUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
    private val snapShotRepository: SnapShotRepository,
    private val gifEncoderRepository: GifEncoderRepository,
    private val fileSelectorRepository: FileSelectorRepository
) {
    init {
        gifEncoderRepository.savedEvent {
            Log.i(TAG, "savedEvent gif: ${it.absoluteFile}")
            fileSelectorRepository.deploy(it)
        }
    }

    suspend operator fun invoke(size: Size, intervalSeconds: Int) {
        coroutineScope {
            try {
                snapshot(size, intervalSeconds)
            } finally {
                Log.d(TAG, "Stop snapshot")
                gifEncoderRepository.close()
            }
        }
    }

    private suspend fun CoroutineScope.snapshot(size: Size, intervalSeconds: Int) {
        timerRepository.tickerFlow(intervalSeconds.seconds).collect {
            snapShotRepository.takePhoto(size)
                .onSuccess {
                    Log.v(TAG, "snapshot success")
                    gifEncoderRepository.pushFrame(it)
                }
                .onFailure { Log.e(TAG, "Failed to snapshot", it) }
        }
    }

    private companion object {
        const val TAG = "SnapshotUseCase"
    }
}
