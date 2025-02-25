package ai.fd.thinklet.library.lifelog.data.mic

import kotlinx.coroutines.flow.Flow

/**
 * マイクからの音声データを取得するリポジトリ
 */
interface MicRepository {
    /**
     * 録音を開始する
     * @return 録音データのFlow.
     */
    fun startRecording(): Flow<Result<ByteArray>>
}
