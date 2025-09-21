package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.strategy.ConcurrencyLimit.Companion.of


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