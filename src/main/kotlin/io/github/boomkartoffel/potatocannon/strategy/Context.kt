package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.exception.ContextFailureException
import io.github.boomkartoffel.potatocannon.result.Result
import java.util.concurrent.ConcurrentHashMap


/**
 * Mutable, thread-safe key–value store used to pass data between requests in a single run
 * or to seed defaults at the cannon level.
 *
 * ### Typical uses
 * - **Seed values globally** (attach to the cannon), e.g. tokens, tenant IDs.
 * - **Capture values from a response** (see [CaptureToContext]) and reuse them in later requests.
 * - **Resolve request settings at send-time** (see [ResolveFromContext]).
 *
 * ### Global vs Session context
 * - **Global context** (see [UseGlobalContext]): long-lived values shared by all fires made with a
 *   cannon instance. Ideal for data you prepare once (e.g., an auth token) and want applied to
 *   *every* request across all test functions using that cannon.
 * - **Session context** (see [UseSessionContext]): short-lived, per-test (or per-scenario) values.
 *   When enabled, captures (e.g., [CaptureToContext.session]) write into the session bucket and
 *   **do not** spill into global state. Resolvers read **session first, then global**.
 *
 * ### Auth use case (Global)
 * In `@BeforeAll`, obtain a token and store it in the **Global** context; add a
 * `resolveFromContext { ... }` setting that turns it into an `Authorization` header.
 * All subsequent requests from that cannon will automatically carry the header—across test
 * functions—without each test repeating the login step.
 *
 * ```kotlin
 * @BeforeAll
 * fun setup() {
 *   val global = PotatoCannonContext().apply { this["token"] = "Bearer abc123" }
 *   cannon = baseCannon
 *     .addSettings(UseGlobalContext(global))
 *     .addSettings(resolveFromContext { ctx ->
 *       ctx.get<String>("token")?.let { BearerAuth(it) }
 *     })
 * }
 * ```
 *
 * ### Per-test isolation (Session)
 * In individual tests, enable **Session** context to capture and reuse values **only** within the
 * test’s scope, preventing pollution of global data:
 *
 * ```kotlin
 * @Test
 * fun my_test() {
 *   val c = cannon.addSettings(UseSessionContext()) // isolates this test's data
 *
 *   val login = Potato(
 *     path = "/login",
 *     // captured into SESSION context, not global:
 *     CaptureToContext.session("token") { r, _ -> "Bearer " + r.responseText() }
 *   )
 *   c.fire(login)
 *
 *    val secured = Potato(
 *     path = "/secured",
 *     resolveFromContext { ctx -> ctx.get<String>("token")?.let { BearerAuth(it) } }
 *   )
 *
 *   c.fire(secured) // resolveFromContext sees session["token"] first, then global
 * }
 * ```
 *
 * ### Key semantics
 * Keys are plain [String]s—choose stable names to avoid collisions (e.g., keep constants in an object).
 *
 * ### Notes
 * - The map is thread-safe. Still, prefer **Session** context to avoid cross-test interference
 * - If multiple contexts are attached at the cannon level, the **last one wins** for writes;
 *   for reads the cannon consults **Session first, then Global**.
 * - **Auto-session-context per fire:** It is not necessary to add a context manually. If no context settings are provided, the cannon creates a fresh
 *   **session context** that is shared by all potatoes within a single `.fire(...)` call.
 *   This lets steps in the same batch capture and reuse values without leaking to other fires.
 * - **Persistent session:** If you add `UseSessionContext` to the cannon, that session context
 *   persists across **subsequent** `.fire(...)` calls on that cannon instance, enabling chaining:
 *   ```
 *   cannon.addSettings(UseSessionContext())
 *     .fire(loginPotato)      // captures into session
 *     .fire(securedPotato)    // reads from the same session
 *   ```
 * - **Avoid global UseSessionContext:** Do not attach `UseSessionContext` at a global/shared cannon (e.g. in the `@BeforeAll`)
 *   that’s reused across tests; it effectively behaves like a global store and can leak data
 *   between tests. Use the `UseSessionContext()` in each test function so the session is isolated to that test.
 *
 * @since 0.1.0
 */
class PotatoCannonContext {
    private val map = ConcurrentHashMap<String, Any>()

    /** Associates [value] with [key]. Overwrites any existing value. */
    operator fun set(key: String, value: Any) {
        map[key] = value
    }

    /**
     * Retrieves the value for [key] as [T] or null if not present or of the wrong type.
     *
     * @return the value for [key] cast to [T]
     *
     * ### Kotlin
     * ```
     * val token: String? = ctx.get("token")   // or: ctx["token"]
     * val retries: Int? = ctx["retries"]
     * ```
     * @since 0.1.0
     */
    @JvmSynthetic
    inline operator fun <reified T : Any> get(key: String): T? =
        get(key, T::class.java)


    /**
     * Retrieves the value for [key] as an instance of [type].
     *
     * Java-friendly accessor that performs runtime type checking and returns `null` if the value is not available or not of the expected type.
     *
     * @param key the context key to look up
     * @param type the expected runtime class of the value
     * @return the value for [key] cast to [T]
     * @since 0.1.0
     */
    fun <T : Any> get(key: String, type: Class<T>): T? {
        val v = map[key] ?: return null
        if (!type.isInstance(v)) {
            return null
        }
        return type.cast(v)
    }

}


/**
 * Captures a value from a [Result] and optionally the already existing (values) from [PotatoCannonContext] and writes it into the shared [PotatoCannonContext]
 * **after** the request completes but **before** verification/expectations run.
 *
 * Use this to extract an ID/token/etc. from one response and reuse it in later requests.
 * The write target is explicit: **session** or **global** (see factory methods below).
 *
 * Retrieval later via [ResolveFromContext] follows **session → global** precedence.
 *
 * ### Kotlin examples
 * ```kotlin
 * // Save the response text as "myKey" into the session context
 * CaptureToContext.session("myKey") { it.responseText() }
 *
 * // Save a derived value that also reads from context
 * CaptureToContext.session("counter") { r, ctx ->
 *     val prev = ctx.get<Int>("counter") ?: 0
 *     prev + 1
 * }
 *
 * // Save to the global context (requires a configured global context)
 * CaptureToContext.global("tenantId") { r -> r.responseText() }
 * ```
 *
 * ### Java example
 * ```java
 * // Save the response text as "myKey" into the session context
 * CaptureToContext cap = CaptureToContext.session("myKey", res -> res.responseText());
 * ```
 *
 * Later, use it:
 * ```kotlin
 * ResolveFromContext { ctx -> CustomHeader("X-My-Key", ctx.get<String>("myKey") ?: "") }
 * ```
 *
 * Notes:
 * - If the same `key` already exists at the chosen target, the new value **overwrites** it.
 * - **Global writes:** if no global context has been configured on the cannon,
 *   the global factory methods will throw [ContextFailureException]
 *   when applied.
 *
 * @param key Context key to write to.
 * @param fn  Function that computes the value from the [Result] and current [ContextView].
 * @throws ContextFailureException if no global context has been configured on the cannon and [CaptureToContext.global] is used.
 * @since 0.1.0
 */
class CaptureToContext private constructor(
    val key: String,
    val target: ContextTarget,
    val fn: (Result, ContextView) -> Any
) : PotatoSetting {

    companion object {
        /**
         * Captures into the **session** context.
         */
        @JvmStatic
        fun session(key: String, fn: (Result, ContextView) -> Any): CaptureToContext =
            CaptureToContext(key, ContextTarget.SESSION, fn)

        /**
         * Captures into the **global** context.
         *
         * @throws ContextFailureException if no global context has been configured on the cannon.
         */
        @JvmStatic
        fun global(key: String, fn: (Result, ContextView) -> Any): CaptureToContext =
            CaptureToContext(key, ContextTarget.GLOBAL, fn)

        /**
         * Captures into the **session** context (ignores [ContextView] in the lambda).
         */
        @JvmStatic
        fun session(key: String, fn: (Result) -> Any): CaptureToContext =
            CaptureToContext(key, ContextTarget.SESSION) { r, _ -> fn(r) }

        /**
         * Captures into the **global** context (ignores [ContextView] in the lambda).
         *
         * @throws ContextFailureException if no global context has been configured on the cannon.
         */
        @JvmStatic
        fun global(key: String, fn: (Result) -> Any): CaptureToContext =
            CaptureToContext(key, ContextTarget.GLOBAL) { r, _ -> fn(r) }
    }
}

enum class ContextTarget {
    GLOBAL,
    SESSION
}


/**
 * A setting that is materialized at **send-time** using values from the shared [PotatoCannonContext].
 *
 * Resolution follows a precedence order:
 * - The **session context** is checked first. If the requested key exists there, its value is applied.
 * - If not found in the session context, the **global context** is checked as a fallback.
 *
 * This allows you to defer building concrete settings (e.g., `QueryParam`, `Header`, `LogCommentary`, etc.)
 * until just before the request is sent—after earlier requests may have populated either context.
 *
 * ### Example
 * ```
 * // Read "myKey" from session or global context and turn it into a query parameter
 * resolveFromContext { ctx ->
 *     QueryParam("number", ctx.get<String>("myKey"))
 * }
 * ```
 *
 * @since 0.1.0
 * @see PotatoCannonContext
 * @see Cannon.withSessionContext
 * @see Cannon.withGlobalContext
 * @see resolveFromContext
 * @see CaptureToContext
 */
fun interface ResolveFromContext : PotatoSetting, CannonSetting {

    /**
     * Produces a concrete [PotatoSetting] based on the current [PotatoCannonContext].
     *
     * If the function returns `null`, no setting is applied.
     *
     * @param ctx the run’s context containing previously captured or seeded values
     * @return the materialized setting to apply to this request
     */
    fun materialize(ctx: ContextView): PotatoSetting?
}


/**
 * Convenience factory for [ResolveFromContext] that defers creation of a single [PotatoSetting]
 * until send-time.
 *
 * ### Example
 * ```
 *   // Resolve Authorization header from the captured token at send-time
 *   resolveFromContext { ctx ->
 *     val token = ctx.get<String>("token")
 *     BearerAuth(token)
 *   }
 * ```
 *
 * @param block lambda that inspects [PotatoCannonContext] and returns a concrete [PotatoSetting]
 * @return a [ResolveFromContext] wrapper that the engine will materialize just-in-time
 *
 * @see ResolveFromContext
 * @see CaptureToContext
 * @see PotatoCannonContext
 * @since 0.1.0
 */
fun resolveFromContext(block: (ContextView) -> PotatoSetting?): ResolveFromContext =
    ResolveFromContext { ctx -> block(ctx) }


/**
 * Read-only view of the aggregated context available to resolvers at send-time.
 *
 * Lookup precedence is **session context first**, then **global context** as a fallback
 * (i.e., *session → global*). Implementations do not allow mutation.
 *
 * Typical usage is inside `resolveFromContext { ... }` to materialize settings from values
 * captured by earlier requests.
 *
 * ### Example
 * ```
 * resolveFromContext { ctx ->
 *   val token: String? = ctx.get("authToken", String::class.java)
 *   token?.let { Header("Authorization", "Bearer $it") }
 * }
 * ```
 *
 * @since 0.1.0
 * @see ResolveFromContext
 * @see Cannon.withSessionContext
 * @see Cannon.withGlobalContext
 */
sealed interface ContextView {

    /**
     * Retrieves a value by key with the requested type.
     *
     * The resolver checks the **session** context first and, if not found, the **global** context.
     * If the key is missing or the value is not assignable to [type], `null` is returned.
     *
     * @param key The context key to look up.
     * @param type The expected Java class of the value to retrieve.
     * @return The value cast to [T], or `null` if absent or incompatible.
     * @since 0.1.0
     */
    fun <T : Any> get(key: String, type: Class<T>): T?
}


/**
 * Retrieves a value from the aggregated context using array-style access.
 *
 * This is an inline, reified convenience for [ContextView.get] that infers the expected type
 * from the call site: `ctx["key"]<T>()` becomes `ctx["key"]` with `T` inferred.
 *
 * Lookup precedence follows the context rules: **session first**, then **global** (session → global).
 * If the key is absent or not assignable to `T`, this returns `null`.
 *
 * Marked **@JvmSynthetic** to hide the operator from Java callers (use [ContextView.get] instead).
 *
 * ### Example
 * ```
 * resolveFromContext { ctx ->
 *   val token: String? = ctx["authToken"]
 *   token?.let { Header("Authorization", "Bearer $it") }
 * }
 * ```
 *
 * @param key The context key to look up.
 * @return The value cast to [T], or `null` if missing or incompatible.
 * @since 0.1.0
 * @see ContextView.get
 * @see ResolveFromContext
 */
@JvmSynthetic
inline operator fun <reified T : Any> ContextView.get(key: String): T? =
    get(key, T::class.java)



internal class CompositeContext(
    private val session: PotatoCannonContext,   // may be null
    private val global: PotatoCannonContext?
) : ContextView {
    override fun <T : Any> get(key: String, type: Class<T>): T? =
        session.get(key, type) ?: global?.get(key, type)

    fun set(key: String, target: ContextTarget, value: Any) {
        when (target) {
            ContextTarget.GLOBAL -> {
                if (global == null) {
                    throw ContextFailureException("Cannot write the key '$key' with the value of type ${value.javaClass} to the global context if it has not been created. Add a global context to the cannon settings first or use the session context.")
                }
                global[key] = value
            }

            ContextTarget.SESSION -> session[key] = value
        }
    }
}

class UseGlobalContext(val ctx: PotatoCannonContext) : CannonSetting {
    constructor() : this(PotatoCannonContext())
}

class UseSessionContext(val ctx: PotatoCannonContext) : CannonSetting {
    constructor() : this(PotatoCannonContext())
}