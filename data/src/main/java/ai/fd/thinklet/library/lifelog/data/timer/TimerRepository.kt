package ai.fd.thinklet.library.lifelog.data.timer

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * タイマーイベントを生成するリポジトリ
 */
interface TimerRepository {
    /**
     * [period] ごとにイベントを生成するFlowを返す．
     */
    fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO): Flow<Unit>
}
