package com.sdtech.studio.performance

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Single central rendering dispatcher with limited parallelism = 2 and cooperative
 * coroutine cancellation so scrolling or leaving a screen immediately cancels
 * queued or in-flight renders.
 */
object RenderDispatcher {
    const val MAX_PARALLELISM = 2

    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(MAX_PARALLELISM)

    private val activeCount = AtomicInteger(0)
    private val maxActiveCount = AtomicInteger(0)

    val activeRenders: Int get() = activeCount.get()
    val maxObservedConcurrency: Int get() = maxActiveCount.get()

    fun resetStats() {
        activeCount.set(0)
        maxActiveCount.set(0)
    }

    suspend fun <T> withRenderContext(block: suspend () -> T): T = withContext(dispatcher) {
        coroutineContext.ensureActive()
        val nowActive = activeCount.incrementAndGet()
        maxActiveCount.updateAndGet { prev -> if (nowActive > prev) nowActive else prev }
        try {
            val result = block()
            coroutineContext.ensureActive()
            result
        } finally {
            activeCount.decrementAndGet()
        }
    }
}
