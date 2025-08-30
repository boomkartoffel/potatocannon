package io.github.boomkartoffel.potatocannon.cannon

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

internal object ParallelExecutorService {

    internal fun taskExecutor(): ExecutorService {
        // Try Java 21+ virtual threads without a hard reference
        try {
            val klass = Class.forName("java.util.concurrent.Executors")
            val m = klass.getMethod("newVirtualThreadPerTaskExecutor")
            return m.invoke(null) as ExecutorService
        } catch (_: Throwable) {
            // Not on Java 21+, or blocked by policy -> fall back
        }
        return Executors.newCachedThreadPool(daemonThreadFactory())
    }

    private fun daemonThreadFactory(): ThreadFactory {
        val n = AtomicInteger(1)
        return ThreadFactory { r ->
            Thread(r, "PotatoCannonThread-${n.andIncrement}").apply { isDaemon = true }
        }
    }
}