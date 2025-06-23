package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.domain.MicRecordUseCase
import ai.fd.thinklet.app.lifelog.domain.SnapshotUseCase
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.file.impl.FileSelectorRepositoryImpl
import ai.fd.thinklet.library.lifelog.data.network.NetworkRepository
import ai.fd.thinklet.library.lifelog.data.s3.S3UploadRepository
import ai.fd.thinklet.library.lifelog.data.upload.UploadQueueRepository
import ai.fd.thinklet.app.lifelog.ui.theme.LifelogTheme
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
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
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    if (options.enabledMic) micRecordUseCase()
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

    private companion object {
        const val TAG = "MainActivity"
    }
}
