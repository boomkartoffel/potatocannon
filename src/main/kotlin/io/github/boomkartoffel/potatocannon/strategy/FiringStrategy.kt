package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.strategy.ConcurrencyLimit.Companion.of
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.MAX
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.MIN
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.of
import io.github.boomkartoffel.potatocannon.strategy.RetryDelayPolicy.NONE
import io.github.boomkartoffel.potatocannon.strategy.RetryDelayPolicy.PROGRESSIVE


/**
 * Caps the number of parallel requests that may be in-flight at any given time.
 *
 * Valid range is **1 to 1000** (inclusive). Use [of] to coerce an arbitrary value into range
 * without throwing, or construct directly (which will throw if the value is out of range).
 *
 * @property value The maximum number of concurrent attempts.
 * @throws IllegalArgumentException if [value] is outside the range.
 * @since 0.1.0
 */
class ConcurrencyLimit(val value: Int) : CannonSetting {
    init {
        require(value in MIN..MAX) { "Concurrency must be between $MIN and $MAX, was $value" }
    }

    companion object {
        /** Default concurrency limit used when none is provided. */
        const val DEFAULT: Int = 500

        /** Minimum allowed concurrency (effectively sequential). */
        const val MIN: Int = 1

        /** Maximum allowed concurrency to protect the client host. */
        const val MAX: Int = 1_000

        /**
         * Returns a [ConcurrencyLimit] where [value] is clamped into the valid range.
         *
         * Prefer this factory when you want robustness over failing fast.
         *
         * @param value desired concurrency
         * @return a limit guaranteed to be within the valid range
         */
        fun of(value: Int): ConcurrencyLimit = ConcurrencyLimit(value.coerceIn(MIN, MAX))
    }
}

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
 * Controls the delay between retry attempts when a request fails to send.
 *
 * # Strategies
 * - [PROGRESSIVE] — Uses an increasing backoff:
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
enum class RetryDelayPolicy : CannonSetting, PotatoSetting {
    /** Increasing delay per attempt (see table above). */
    PROGRESSIVE,

    /** No delay between attempts (immediate retries). */
    NONE
}

/**
 * Per-request timeout (in milliseconds) used when waiting for the request to finalize with a valid response.
 *
 * Note: This timeout only covers the time to receive the response headers. It does not include the time to read the full response body.
 *
 * Valid range is between **100 ms and 2 minutes** (inclusive). Use [of] to clamp without throwing,
 * or construct directly (which will throw if the value is out of range).
 *
 * @property durationMillis Timeout in milliseconds for receiving response headers.
 * @throws IllegalArgumentException if [durationMillis] is outside the range.
 * @since 0.1.0
 */
class RequestTimeout(val durationMillis: Long) : CannonSetting, PotatoSetting {
    init {
        require(durationMillis in MIN..MAX) {
            "Request timeout must be between $MIN and $MAX ms, was $durationMillis ms"
        }
    }

    companion object {
        /** Default per-request timeout (10 seconds). */
        const val DEFAULT: Long = 10_000

        /** Minimum allowed timeout to avoid unrealistic immediate failures. */
        const val MIN: Long = 100

        /** Maximum allowed timeout to prevent unbounded waits (2 minutes). */
        const val MAX: Long = 120_000

        /**
         * Returns a [RequestTimeout] with [durationMillis] clamped into **[MIN]..[MAX]**.
         */
        fun of(durationMillis: Long): RequestTimeout =
            RequestTimeout(durationMillis.coerceIn(MIN, MAX))
    }
}
