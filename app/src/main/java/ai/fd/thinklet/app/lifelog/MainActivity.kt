package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.domain.MicRecordUseCase
import ai.fd.thinklet.app.lifelog.domain.SnapshotUseCase
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.file.impl.FileSelectorRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import ai.fd.thinklet.app.lifelog.ui.theme.LifelogTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var pendingRecordingStart = false
    
    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
    }
    @Inject
    lateinit var snapshotUseCase: SnapshotUseCase

    @Inject
    lateinit var micRecordUseCase: MicRecordUseCase

    @Inject
    lateinit var s3UploadRepository: S3UploadRepository

    @Inject
    lateinit var networkRepository: NetworkRepository

    @Inject
    lateinit var uploadQueueRepository: UploadQueueRepository

    @Inject
    lateinit var fileSelectorRepository: FileSelectorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LifelogTheme {
                Scaffold { padding ->
                    Text(
                        modifier = Modifier.padding(padding),
                        text = "LifeLog application is running..."
                    )
                }
            }
        }

        val options = LifeLogArgs.get(intent.extras)
        Log.i(TAG, "options $options")
        
        // ストレージパスの設定
        if (!options.storagePath.isNullOrEmpty()) {
            (fileSelectorRepository as FileSelectorRepositoryImpl).setStoragePath(options.storagePath)
            Log.i(TAG, "Custom storage path set: ${options.storagePath}")
        }
        
        // S3設定の初期化
        if (options.s3Enabled && 
            !options.s3BucketName.isNullOrEmpty() && 
            !options.s3Region.isNullOrEmpty() && 
            !options.s3AccessKey.isNullOrEmpty() && 
            !options.s3SecretKey.isNullOrEmpty()) {
            s3UploadRepository.configure(
                bucketName = options.s3BucketName,
                region = options.s3Region,
                accessKey = options.s3AccessKey,
                secretKey = options.s3SecretKey,
                endpoint = options.s3Endpoint
            )
            val endpointInfo = if (options.s3Endpoint.isNullOrEmpty()) "AWS S3" else "Custom endpoint: ${options.s3Endpoint}"
            Log.i(TAG, "S3 upload configured for bucket: ${options.s3BucketName} ($endpointInfo)")
        } else {
            Log.i(TAG, "S3 upload disabled or not properly configured")
        }
        
        lifecycleScope.launch {
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    snapshotUseCase(size = options.size, intervalSeconds = options.intervalSeconds)
                }
            }
            // 音声録音のハンドリング
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    if (options.enabledMic) {
                        startRecordingWithPermissionCheck()
                    }
                }
            }
            // WiFi接続状態の監視とアップロードキューの処理
            if (options.s3Enabled) {
                launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        networkRepository.isWifiConnectedFlow().collect { isWifiConnected ->
                            if (isWifiConnected) {
                                Log.i(TAG, "WiFi connected, processing pending uploads")
                                uploadQueueRepository.processPendingUploads()
                            } else {
                                Log.d(TAG, "WiFi disconnected")
                            }
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun startRecordingWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Audio permission granted, starting recording")
            micRecordUseCase()
        } else {
            Log.i(TAG, "Audio permission not granted, requesting permission")
            pendingRecordingStart = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Audio permission granted by user")
                if (pendingRecordingStart) {
                    pendingRecordingStart = false
                    lifecycleScope.launch {
                        micRecordUseCase()
                    }
                }
            } else {
                Log.w(TAG, "Audio permission denied by user")
                pendingRecordingStart = false
            }
        }
    }
}
