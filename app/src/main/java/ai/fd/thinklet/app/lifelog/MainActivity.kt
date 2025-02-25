package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.domain.MicRecordUseCase
import ai.fd.thinklet.app.lifelog.domain.SnapshotUseCase
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
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
