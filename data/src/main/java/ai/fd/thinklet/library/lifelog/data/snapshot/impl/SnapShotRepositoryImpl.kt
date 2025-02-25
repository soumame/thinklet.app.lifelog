package ai.fd.thinklet.library.lifelog.data.snapshot.impl

import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.vibrate.VibrateRepository
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.MainThread
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

internal class SnapShotRepositoryImpl @Inject constructor(
    private val context: Context,
    private val vibrateRepository: VibrateRepository,
    private val executor: Executor = Executors.newSingleThreadExecutor()
) : SnapShotRepository {
    private val lifecycleOwner = CustomLifecycleOwner().apply { onStop() }

    init {
        CameraXPatch.apply()
    }

    @MainThread
    override suspend fun takePhoto(size: Size?): Result<Bitmap> {
        return takePhotoInner(size ?: defaultSize)
    }

    private suspend fun takePhotoInner(size: Size): Result<Bitmap> {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            Log.w(TAG, "too busy")
            return Result.failure(IllegalStateException("too busy"))
        }
        val camProvider = ProcessCameraProvider.getInstance(context).await()
        val (camSelector, _) = getMountAngles()[0]

        return try {
            configureWithPhoto(
                size = size,
                provider = camProvider,
                cameraSelector = camSelector
            ).first()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @MainThread
    private fun configureWithPhoto(
        size: Size,
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector
    ) = callbackFlow<Result<Bitmap>> {
        lifecycleOwner.onStart()

        // 最初にunbind
        try {
            provider.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbindAll")
            trySend(Result.failure(e))
            return@callbackFlow
        }

        // 撮影用のユースケースを取得
        val imageCapture = ImageCapture.Builder().setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        size,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                    )
                )
                .build()
        ).build()

        // bind開始
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed")
            trySend(Result.failure(e))
            return@callbackFlow
        }
        delay(100L) // WA: カメラが利用できないことがあるため，撮影前に遅延処理をいれる．

        val callback = object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                Log.d(TAG, "onCaptureSuccess")
                vibrateRepository.vibrate(VibrateRepository.SHOOT)
                trySend(Result.success(image.toBitmapWithRotate()))
                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.d(TAG, "onError", exception)
                trySend(Result.failure(exception))
            }
        }

        // 撮影リクエスト
        kotlin.runCatching {
            imageCapture.takePicture(executor, callback)
        }.onFailure {
            Log.e(TAG, "Failed to takePicture")
            trySend(Result.failure(it))
        }

        awaitClose {
            Log.d(TAG, "awaitClose")
            kotlin.runCatching {
                lifecycleOwner.onStop()
                provider.unbindAll()
            }.onFailure {
                Log.e(TAG, "Failed invokeOnCancellation tasks")
            }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun getMountAngles(): List<Pair<CameraSelector, Int>> {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val ret = mutableListOf<Pair<CameraSelector, Int>>()
        cameraManager.cameraIdList.forEach { camId ->
            val angle = cameraManager.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: return@forEach
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { it2 ->
                    it2.filter { Camera2CameraInfo.from(it).cameraId == camId }
                }.build()
            ret.add(Pair(cameraSelector, angle))
        }
        return ret.toList()
    }

    private fun ImageProxy.toBitmapWithRotate(): Bitmap {
        return when (this.imageInfo.rotationDegrees) {
            0 -> this.toBitmap()
            else -> {
                val bmp = this.toBitmap()
                Bitmap.createBitmap(
                    bmp,
                    0,
                    0,
                    bmp.width,
                    bmp.height,
                    Matrix().also { it -> it.postRotate(this.imageInfo.rotationDegrees.toFloat()) },
                    true
                )
            }
        }
    }

    /**
     * CameraX向けのTHINKLETの高速化パッチ
     */
    private object CameraXPatch {
        private var patched: Boolean = false

        fun apply() {
            if (!patched && Build.MODEL.contains("THINKLET")) {
                ProcessCameraProvider.configureInstance(
                    CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                        .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
                        .setMinimumLoggingLevel(Log.WARN)
                        .build()
                )
                patched = true
            }
        }
    }

    private companion object {
        const val TAG = "SnapShotRepository"
        val defaultSize = Size(1440, 1080)
    }
}
