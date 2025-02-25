package ai.fd.thinklet.library.lifelog.data.mic.impl

import ai.fd.thinklet.library.lifelog.data.mic.MicRepository
import ai.fd.thinklet.sdk.audio.MultiChannelAudioRecord.Channel
import ai.fd.thinklet.sdk.audio.RawAudioRecordWrapper
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

@SuppressLint("MissingPermission")
internal class MicRepositoryImpl @Inject constructor(
    private val context: Context
) : MicRepository {
    private val rawRecorder = RawAudioRecordWrapper(
        channel = Channel.CHANNEL_FIVE,
        outputChannel = RawAudioRecordWrapper.RawAudioOutputChannel.STEREO
    )

    override fun startRecording(): Flow<Result<ByteArray>> = callbackFlow {
        if (!rawRecorder.prepare(context)) {
            Log.e(TAG, "Failed to prepare rawRecorder")
            trySend(Result.failure(IllegalStateException("Failed to prepare rawRecorder")))
            return@callbackFlow
        }

        rawRecorder.start(null, object : RawAudioRecordWrapper.IRawAudioRecorder {
            override fun onFailed(throwable: Throwable) {
                trySend(Result.failure(throwable))
            }

            override fun onReceivedPcmData(pcmData: ByteArray) {
                trySend(Result.success(pcmData))
            }
        })
        awaitClose {
            rawRecorder.stop()
        }
    }

    private companion object {
        const val TAG = "MicRepository"
    }
}
