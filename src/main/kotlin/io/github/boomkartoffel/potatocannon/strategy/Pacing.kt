package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.strategy.Pacing.Companion.MAX_MILLIS
import io.github.boomkartoffel.potatocannon.strategy.Pacing.Companion.of


/**
 * Pacing interval applied **between potatoes** — i.e., between consecutive requests.
 *
 * This is not a per-request delay or a timeout, and it does **not** govern retries.
 * It’s a **throttle** that spaces out when new potatoes begin, without affecting in-flight I/O.
 *
 * You can specify a **constant** interval (in milliseconds) or provide a function
 * that computes the interval from the current [PotatoCannonContext].
 *
 * Semantics:
 * - Not applied before the very first potato in a `fire(...)` run.
 * - The cannon waits this interval after a potato finishes before starting the next potato.
 *
 * Retries:
 * - Pacing does **not** apply to retries. If an attempt fails, the next attempt for the **same**
 *   potato follows the configured [RetryDelay] (which solely governs retry timing).
 *
 * Interactions:
 * - Independent of retry backoff. Pacing spaces **potatoes**; backoff spaces **retries**.
 * - Does not change `RequestTimeout`.
 * - If a Pacing is set, [FireMode.SEQUENTIAL] is used (regardless of any explicit [FireMode] setting).
 *
 * Valid range: **0 ms to 5 minutes** (inclusive).
 * - Use the primary **constant** constructor to enforce the range eagerly (throws if out of range).
 * - Use [of] builders to clamp without throwing.
 * - For the **function** constructor, the value is validated **at call time** when resolved.
 *
 * @since 0.1.0
 */
class Pacing private constructor(
    private val provider: (ContextView) -> Long,
    val isZeroPacing: Boolean
) : CannonSetting {

    /**
     * Constant interval (in milliseconds); validated **eagerly** at construction.
     *
     * Semantics:
     * - Applies the same pacing interval **between potatoes**
     * - A value of **0** means “no pacing” (i.e., this setting is effectively disabled and the
     *   cannon may keep the configured fire mode, including PARALLEL).
     * - A **positive** value will enforce [FireMode.SEQUENTIAL]
     *
     * Validation:
     * - Must be within `[0, [MAX_MILLIS]]` (inclusive). Validation happens on construction.
     * - Prefer this constructor when you want **fail-fast** behavior on bad configuration.
     * - If you want non-throwing construction, use `Pacing.of(ms)` which **clamps** into range.
     *
     * @param intervalMillis constant pacing interval in milliseconds.
     * @throws IllegalArgumentException if `intervalMillis` is outside `[0, [MAX_MILLIS]]`.
     */
    constructor(intervalMillis: Long) : this(
        { _ -> intervalMillis },
        isZeroPacing = intervalMillis == 0L
    ) {
        require(intervalMillis in 0..MAX_MILLIS) {
            "Pacing interval must be between 0 and $MAX_MILLIS ms (inclusive), was $intervalMillis ms"
        }
    }

    /**
     * Context-based interval supplier.
     *
     * Semantics:
     * - Applies a pacing interval **between potatoes** according to a custom function.
     * - Use of this pacing strategy will enforce [FireMode.SEQUENTIAL]
     *
     * Evaluation timing:
     * - This function is evaluated the first time **right before the second potato starts**
     *   (i.e., after the first potato has fully finished, including retries), and then
     *   again before each subsequent potato starts.
     * - Because earlier potatoes may have modified the [PotatoCannonContext] (e.g., via
     *   [CaptureToContext]), make sure your logic accounts for those updates.
     * - It is **not** invoked for retries; retries are governed solely by [RetryDelay].
     *
     * Note on validation:
     * - When the function is invoked and the result is out of range, the pacing interval will be coerced into the valid range [0, [MAX_MILLIS]].
     *
     * @param fn function that computes the interval from the current [PotatoCannonContext].
     * @throws RequestPreparationException if the function throws when invoked.
     */
    constructor(fn: (ContextView) -> Long) : this(fn, isZeroPacing = false)

    /**
     * Resolve the pacing interval for the given [PotatoCannonContext], clamped to [0, MAX_MILLIS].
     * If this instance was created with the function constructor, out-of-range values will throw.
     */
    fun intervalMillis(ctx: ContextView): Long {
        val raw = try {
            provider(ctx)
        } catch (t: Throwable) {
            throw RequestPreparationException("Pacing function threw while resolving interval", t)
        }

        return raw.coerceIn(0, MAX_MILLIS)
    }

    companion object {
        /** 5 minutes in milliseconds. */
        const val MAX_MILLIS: Long = 5 * 60 * 1000

        /**
         * Clamp a constant interval to the valid range [0, MAX_MILLIS] without throwing.
         * Negative values become 0; values above the max become [MAX_MILLIS].
         */
        @JvmStatic
        fun of(intervalMillis: Long): Pacing {
            val clamped = intervalMillis.coerceIn(0, MAX_MILLIS)
            return Pacing(clamped)
        }

        /**
         * Build a context-based pacing with clamping (no throws). The provided function may return
         * any value; it will be coerced into [0, MAX_MILLIS] at call time.
         */
        @JvmStatic
        fun of(fn: (ContextView) -> Long): Pacing =
            Pacing({ ctx -> fn(ctx).coerceIn(0, MAX_MILLIS) }, isZeroPacing = false)
    }
}