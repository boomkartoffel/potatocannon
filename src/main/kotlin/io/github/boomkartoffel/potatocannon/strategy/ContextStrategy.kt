package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.result.Result
import java.util.concurrent.ConcurrentHashMap


/**
 * Mutable, thread-safe key–value store used to pass data between requests in a single run
 * or to seed defaults at the cannon level.
 *
 * ### Typical uses
 * - Seed values globally (attach to the cannon), e.g. tokens, tenant IDs.
 * - Capture values from a response (see [CaptureToContext]) and reuse them in later requests.
 * - Resolve request settings at send-time (see [ResolveFromContext]).
 *
 * ### Key semantics
 * Keys are plain [String]s—choose stable names to avoid collisions (e.g., keep constants in an object).
 *
 * ### Example
 * ```
 * val ctx = CannonContext().apply { this["scenarioId"] = "abc-123" }
 * baseCannon.addSettings(ctx)
 * ```
 *
 * It is **not** necessary to add the context manually. If no context is provided at a cannon level, there will be a default empty one created for each firing run.
 * If multiple contexts are added to the cannon, the last one wins.
 *
 * @since 0.1.0
 */
class CannonContext : CannonSetting {
    private val map = ConcurrentHashMap<String, Any>()

    /** Associates [value] with [key]. Overwrites any existing value. */
    operator fun set(key: String, value: Any) {
        map[key] = value
    }

    /**
     * Retrieves the value for [key] as [T].
     *
     * Returns the stored value cast to [T]; never `null`.
     *
     * @return the value for [key] cast to [T]
     * @throws [RequestPreparationException] if [key] is not present or if the value exists but is not of type [T]
     *
     * ### Kotlin
     * ```
     * val token: String = ctx.get("token")   // or: ctx["token"]
     * val retries: Int = ctx["retries"]
     * ```
     */
    @JvmSynthetic
    inline operator fun <reified T : Any> get(key: String): T =
        get(key, T::class.java)


    /**
     * Retrieves the value for [key] as an instance of [type].
     *
     * Java-friendly accessor that performs runtime type checking and never returns `null`.
     *
     * @param key the context key to look up
     * @param type the expected runtime class of the value
     * @return the value for [key] cast to [T]
     * @throws [RequestPreparationException] if [key] is not present or if the value exists but is not of type [T]
     */
    fun <T : Any> get(key: String, type: Class<T>): T {
        val v = map[key] ?: throw RequestPreparationException("No value found in context for the key '$key'", null)
        if (!type.isInstance(v)) {
            throw RequestPreparationException("Value for '$key' is of type ${v.javaClass.name}, but expected is ${type.name}", null)
        }
        return type.cast(v)
    }
}


/**
 * Captures a value from a [Result] and writes it into the shared [CannonContext] after the request completes but before verification.
 *
 * Typical use: extract an ID or token from one response and reuse it in subsequent requests.
 *
 * ### Example
 * ```
 * // Save the response text as "myKey" in the context
 * CaptureToContext("myKey") { r -> r.responseText() }
 * ```
 *
 * Later, you can retrieve it using a [ResolveFromContext] setting:
 *
 * ```
 * ResolveFromContext { ctx -> CustomHeader("X-My-Key", ctx["myKey"]) }
 * ```
 *
 * @param key the context key to write to
 * @param f   function that computes the value from the [Result]
 */
class CaptureToContext(val key: String, val f: (Result) -> Any) : PotatoSetting


/**
 * A setting that is materialized at **send-time** using values from the shared [CannonContext].
 *
 * This allows you to defer building concrete settings (e.g., `QueryParam`, `Header`, `LogCommentary` etc.)
 * until just before the request is sent—after earlier requests may have populated the context.
 *
 * ### Example
 * ```
 * // Read "myKey" from context and turn it into a query parameter
 * resolveFromContext { ctx ->
 *     QueryParam("number", ctx.get<String>("myKey"))
 * }
 * ```
 */
fun interface ResolveFromContext : PotatoSetting {

    /**
     * Produces a concrete [PotatoSetting] based on the current [CannonContext].
     * @param ctx the run’s context containing previously captured or seeded values
     * @return the materialized setting to apply to this request
     */
    fun materialize(ctx: CannonContext): PotatoSetting
}


/**
 * Convenience factory for [ResolveFromContext] that defers creation of a single [PotatoSetting]
 * until send-time.
 *
 * ### Example
 * ```
 *   // Resolve Authorization header from the captured token at send-time
 *   resolveFromContext { c ->
 *     val token = c.get<String>("token") ?: error("Missing auth token in CannonContext")
 *     BearerAuth(token)
 *   }
 * ```
 *
 * @param block lambda that inspects [CannonContext] and returns a concrete [PotatoSetting]
 * @return a [ResolveFromContext] wrapper that the engine will materialize just-in-time
 *
 * @see ResolveFromContext
 * @see CaptureToContext
 * @see CannonContext
 */
fun resolveFromContext(block: (CannonContext) -> PotatoSetting): ResolveFromContext =
    ResolveFromContext { ctx -> block(ctx) }