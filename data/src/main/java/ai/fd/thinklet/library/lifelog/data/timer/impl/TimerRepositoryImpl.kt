package ai.fd.thinklet.library.lifelog.data.timer.impl

import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Duration

internal class TimerRepositoryImpl @Inject constructor() : TimerRepository {
    override fun tickerFlow(
        period: Duration,
        initialDelay: Duration
    ) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }
}
