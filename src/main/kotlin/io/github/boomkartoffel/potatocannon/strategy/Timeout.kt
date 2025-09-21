package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.MAX
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.MIN
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout.Companion.of


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


