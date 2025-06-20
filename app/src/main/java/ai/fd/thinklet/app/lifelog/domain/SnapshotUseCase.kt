package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.jpeg.JpegSaverRepository
import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import android.util.Log
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * 定期的に写真撮影をして，JPEGファイルとして保存するUseCase
 */
class SnapshotUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
    private val snapShotRepository: SnapShotRepository,
    private val jpegSaverRepository: JpegSaverRepository,
    private val fileSelectorRepository: FileSelectorRepository
) {
    init {
        jpegSaverRepository.savedEvent {
            Log.i(TAG, "savedEvent jpeg: ${it.absoluteFile}")
            fileSelectorRepository.deploy(it)
        }
    }

    suspend operator fun invoke(size: Size, intervalSeconds: Int) {
        coroutineScope {
            snapshot(size, intervalSeconds)
        }
    }

    private suspend fun CoroutineScope.snapshot(size: Size, intervalSeconds: Int) {
        timerRepository.tickerFlow(intervalSeconds.seconds).collect {
            snapShotRepository.takePhoto(size)
                .onSuccess { bitmap ->
                    Log.v(TAG, "snapshot success")
                    val result = jpegSaverRepository.saveJpeg(bitmap)
                    result.onSuccess { file ->
                        Log.d(TAG, "JPEG saved: ${file.absolutePath}")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to save JPEG", error)
                    }
                }
                .onFailure { Log.e(TAG, "Failed to snapshot", it) }
        }
    }

    private companion object {
        const val TAG = "SnapshotUseCase"
    }
}
