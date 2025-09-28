package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.exception.RequestSendingFailureException
import io.github.boomkartoffel.potatocannon.strategy.RetryDelay.Companion.MAX_MILLIS
import io.github.boomkartoffel.potatocannon.strategy.RetryDelayPolicy.NONE
import io.github.boomkartoffel.potatocannon.strategy.RetryDelayPolicy.PROGRESSIVE

/**
 * Maximum number of **client-side retry attempts** for transient open/connect failures
 * (e.g., `ConnectException`, `ClosedChannelException`).
 *
 * This does **not** apply to application-level failures (e.g., HTTP 5xx).
 *
 * Valid range is **0 to 10000** (inclusive).
 *
 * @property count Number of retries allowed (0 means no retries).
 * @throws IllegalArgumentException if [count] is outside the range.
 * @since 0.1.0
 */
class RetryLimit(val count: Int) : CannonSetting, PotatoSetting {
    init {
        require(count in MIN..MAX) { "Max retries must be between $MIN and $MAX, was $count" }
    }

    companion object {
        /** Default retry count used when none is provided. */
        const val DEFAULT: Int = 3

        /** Minimum allowed retries (disable retries). */
        const val MIN: Int = 0

        /** Maximum allowed retries to avoid unbounded work. */
        const val MAX: Int = 10_000
    }
}

/**
 * Retry delay strategy (Potato/Cannon setting) with multiple ergonomic constructors:
 *
 * - Constant: `RetryDelay(250)` --> 250 ms every retry
 * - Function of attempt: `RetryDelay { attempt -> 50L * (attempt + 1) }`
 * - Function of attempt + context: `RetryDelay { attempt, ctx -> compute(attempt, ctx) }`
 * - Standard policy: `RetryDelay(RetryDelayPolicy.PROGRESSIVE)`
 *
 * Note
 * - Default is [PROGRESSIVE].
 * - To disable delays, use [NONE] or `RetryDelay(0)`.
 * - If multiple [RetryDelay] settings are provided, the **last one** is used.
 * - In parallel mode the context might be altered by other threads; this could impact the delay depending on the function.
 *
 * Result is clamped to [0, [MAX_MILLIS]].
 *
 * @throws RequestSendingFailureException if the function throws.
 */
class RetryDelay (
    private val fn: (attempt: Int, ctx: ContextView) -> Long
) : PotatoSetting, CannonSetting {

    /** Constant delay. */
    constructor(millis: Long) : this({ _, _ -> millis })

    /** Function of attempt. */
    constructor(f: (attempt: Int) -> Long) : this({ a, _ -> f(a) })

    /** Standard policies. */
    constructor(policy: RetryDelayPolicy) : this({ attempt, _ -> policy.delayMillis(attempt) })

    /** Compute clamped delay (ms) for a given attempt and context. */
    fun delayMillis(attempt: Int, ctx: ContextView): Long {
        val raw = try { fn(attempt, ctx) } catch (ex: Exception) {
            throw RequestSendingFailureException("RetryDelay function threw for attempt $attempt", ex)
        }
        return when {
            raw < 0L -> 0L
            raw > MAX_MILLIS -> MAX_MILLIS
            else -> raw
        }
    }

    companion object {
        /** Safety cap: 10 minutes. */
        const val MAX_MILLIS: Long = 10 * 60 * 1000

        @JvmStatic fun ofMillis(ms: Long) = RetryDelay(ms)
        @JvmStatic fun of(f: (Int) -> Long) = RetryDelay(f)
        @JvmStatic fun of(f: (Int, ContextView) -> Long) = RetryDelay(f)
        @JvmStatic fun of(policy: RetryDelayPolicy) = RetryDelay(policy)
    }
}


/**
 * Controls the delay between retry attempts when a request fails to send.
 *
 * # Strategies
 * - [PROGRESSIVE] (default) — Uses an increasing backoff:
 *   initial steps of **10ms, 25ms, 50ms, 100ms, 200ms** for attempts 0..4,
 *   then grows linearly by 200ms per additional attempt:
 *
 *   | **retry attempt** | 1  | 2  | 3  |  4  |  5  |  6  |  7  |  8  |  9  | …   |
 *   |------------------:|----|----|----|-----|-----|-----|-----|-----|-----|-----|
 *   | **delay (ms)**         | 10 | 25 | 50 | 100 | 200 | 400 | 600 | 800 | 1000| …   |
 *
 * - [NONE] — Disables backoff; retries happen immediately with **0ms** delay.
 *
 * # Notes
 * - Combine with a [RetryLimit] to cap the number of attempts.
 * - In parallel mode the permit is released during the sleep; in sequential mode a
 *   single permit may be held across attempts (see executor semantics in the library).
 *
 */
enum class RetryDelayPolicy {
    /** Increasing delay per attempt. */
    PROGRESSIVE {
        override fun delayMillis(attempt: Int): Long {
            val steps = listOf(10L, 25L, 50L, 100L, 200L)
            return if (attempt < steps.size) steps[attempt]
            else steps.last() * (attempt - steps.size + 2)
        }
    },
    /** No delay (immediate retries). */
    NONE {
        override fun delayMillis(attempt: Int) = 0L
    };

    abstract fun delayMillis(attempt: Int): Long
}