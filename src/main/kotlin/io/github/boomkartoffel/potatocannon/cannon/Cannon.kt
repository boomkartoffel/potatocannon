package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.exception.PotatoCannonException
import io.github.boomkartoffel.potatocannon.exception.RequestExecutionException
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingFailureException
import io.github.boomkartoffel.potatocannon.potato.*
import io.github.boomkartoffel.potatocannon.result.Headers
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.result.log
import io.github.boomkartoffel.potatocannon.strategy.*
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.ProtocolVersion
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.http2.HttpVersionPolicy
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.*

private const val PC_FINAL_REQ_HDRS = "pc.finalRequestHeaders"


/**
 * A configurable HTTP test runner that fires predefined requests ([Potato]) against a target base URL.
 *
 * The `Cannon` supports sequential and parallel execution modes, custom headers, logging, expectations,
 * and other reusable setting strategies. Designed for functional testing, stress testing, or
 * automation of REST-like APIs.
 *
 * @property baseUrl The root URL used to resolve all relative request paths.
 * @property settings A list of reusable strategies or behaviors that affect request firing.
 * @since 0.1.0
 */
class Cannon {
    private val baseUrl: String
    private val settings: List<CannonSetting>
    private val negotiateClient: CloseableHttpAsyncClient
    private val h1Client: CloseableHttpAsyncClient
    private val h2Client: CloseableHttpAsyncClient

    private fun cmWithPolicy(policy: HttpVersionPolicy) =
        PoolingAsyncClientConnectionManagerBuilder.create()
            .setDefaultTlsConfig(
                TlsConfig.custom()
                    .setVersionPolicy(policy)
                    .build()
            )
            .setMaxConnTotal(ConcurrencyLimit.MAX)
            .setMaxConnPerRoute(ConcurrencyLimit.MAX)
            .build()

    private val finalHeaderInterceptor = HttpRequestInterceptor { request, _, context: HttpContext ->
        // Group into Map<String, List<String>> with lowercase names
        val finalHeaders = request.headers
            .groupBy({ it.name.lowercase() }, { it.value })
        // Stash into context so you can read it in the response handler
        context.setAttribute(PC_FINAL_REQ_HDRS, finalHeaders)
    }

    constructor(baseUrl: String) : this(baseUrl, listOf())

    constructor(baseUrl: String, vararg settings: CannonSetting) : this(baseUrl, settings.toList())

    constructor(baseUrl: String, settings: List<CannonSetting>) {
        this.baseUrl = baseUrl
        this.settings = settings
        this.negotiateClient = HttpAsyncClients.custom()
            .setConnectionManager(cmWithPolicy(HttpVersionPolicy.NEGOTIATE))
            .addRequestInterceptorLast(finalHeaderInterceptor)
            .build().apply { start() }
        this.h1Client = HttpAsyncClients.custom()
            .setConnectionManager(cmWithPolicy(HttpVersionPolicy.FORCE_HTTP_1))
            .addRequestInterceptorLast(finalHeaderInterceptor)
            .build().apply { start() }
        this.h2Client = HttpAsyncClients.custom()
            .setConnectionManager(cmWithPolicy(HttpVersionPolicy.FORCE_HTTP_2))
            .addRequestInterceptorLast(finalHeaderInterceptor)
            .build().apply { start() }
    }

    /**
     * Sets the firing mode for this cannon.
     *
     * When set to [FireMode.SEQUENTIAL], potatoes are fired strictly one after another, allowing
     * safe sharing of data via [PotatoCannonContext] across requests. With [FireMode.PARALLEL], multiple
     * potatoes may execute concurrently (subject to [ConcurrencyLimit]).
     *
     * This call adds/overrides a [FireMode] setting and returns a cannon with the updated settings
     *
     * @param mode The desired firing mode, e.g. [FireMode.SEQUENTIAL] or [FireMode.PARALLEL].
     * @return A [Cannon] instance with the provided [FireMode] applied.
     * @since 0.1.0
     */
    fun withFireMode(mode: FireMode): Cannon = addSettings(mode)

    /**
     * Attaches a [PotatoCannonContext] to this cannon for sharing key–value data across potatoes.
     *
     * Values captured earlier (e.g., via `CaptureToContext`) can be retrieved in later requests
     * using [resolveFromContext]. If multiple contexts are
     * added, the most recently added one takes precedence, effectively resetting the previous context. Returns a cannon with the updated settings.
     *
     * @param context The [PotatoCannonContext] to make available during firing.
     * @return A [Cannon] instance with the provided [PotatoCannonContext] applied.
     * @see resolveFromContext
     * @see CaptureToContext
     * @since 0.1.0
     */
    fun withGlobalContext(context: PotatoCannonContext): Cannon = addSettings(UseGlobalContext(context))

    /**
     * Attaches an empty [PotatoCannonContext] to this cannon for sharing key–value data across potatoes.
     *
     * Subsequent requests can populate and read values via `CaptureToContext.global(...)` / [resolveFromContext].
     * If multiple contexts are added, the most recently added one takes precedence. Returns a cannon
     * with the updated settings.
     *
     * @return A [Cannon] instance with an empty [PotatoCannonContext] applied.
     * @see resolveFromContext
     * @see CaptureToContext
     * @since 0.1.0
     */
    fun withGlobalContext(): Cannon = addSettings(UseGlobalContext())

    /**
     * Attaches a session-scoped [PotatoCannonContext] to this cannon.
     *
     * A *session context* is intended for data that should live only within a few firing sessions
     * (e.g., auth tokens, correlation IDs). After setting this Context, **all following** `.fire(...)` calls will be able to access the store. Values can be captured
     * and later resolved via `CaptureToContext.session(...)` / [resolveFromContext]. If multiple contexts are
     * added, the most recently added one takes precedence, effectively resetting the previous context. Returns a cannon with the updated settings.
     *
     * @return A [Cannon] instance with an empty session [PotatoCannonContext] applied.
     * @since 0.1.0
     * @see resolveFromContext
     * @see CaptureToContext
     */
    fun withSessionContext(): Cannon = addSettings(UseSessionContext())

    /**
     * Attaches the given session-scoped [PotatoCannonContext] to this cannon.
     *
     * Use this when you want to seed the session context with initial values. If multiple contexts are added, the most recently added
     * one takes precedence. Returns a cannon with the updated settings.
     *
     * @param ctx The initial session [PotatoCannonContext].
     * @return A [Cannon] instance with the provided session [PotatoCannonContext] applied.
     * @see resolveFromContext
     * @see CaptureToContext
     * @since 0.1.0
     */
    fun withSessionContext(ctx: PotatoCannonContext): Cannon = addSettings(UseSessionContext(ctx))

    /**
     * Returns a new `Cannon` instance with additional [CannonSetting] strategies appended.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined [CannonSetting].
     *
     * @param additionalSettings The new [CannonSetting] strategies to add.
     * @return A new `Cannon` with extended [CannonSetting].
     * @since 0.1.0
     */
    fun addSettings(vararg additionalSettings: CannonSetting): Cannon =
        this.addSettings(additionalSettings.toList())

    /**
     * Returns a new `Cannon` instance with additional [CannonSetting] strategies appended. This means that strategies like [FireMode], which resolve to the last one trumping the previous ones, will be applied.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined [CannonSetting].
     *
     * @param additionalSettings The new [CannonSetting] strategies to add.
     * @return A new `Cannon` with extended [CannonSetting].
     * @since 0.1.0
     */
    fun addSettings(additionalSettings: List<CannonSetting>): Cannon =
        Cannon(baseUrl, settings + additionalSettings)

    /**
     * Fires the givens single or multiple [Potato].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL].
     *
     * @param potatoes The HTTP requests to fire.
     * @return A list of [Result] objects representing the responses.
     * @since 0.1.0
     */
    fun fire(vararg potatoes: FireablePotato): Cannon {
        fireWithResults(potatoes.toList())
        return this
    }

    /**
     * Fires a list of [Potato] requests according to the configured [FireMode].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL] (except when pacing is used, in which case it switches to [FireMode.SEQUENTIAL]).
     *
     * @param potatoes The list of HTTP requests to fire.
     * @return A list of [Result] objects representing the responses.
     * @since 0.1.0
     */
    fun fire(potatoes: List<FireablePotato>): Cannon {
        fireWithResults(potatoes)
        return this
    }

    /**
     * Fires the givens single or multiple [Potato].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL].
     *
     * @param potatoes The HTTP requests to fire.
     * @return A list of [Result] objects representing the responses.
     * @since 0.1.0
     */
    fun fireWithResults(vararg potatoes: FireablePotato): List<Result> {
        return fireWithResults(potatoes.toList())
    }

    /**
     * Fires a list of [Potato] requests according to the configured [FireMode].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL] (except when pacing is used, in which case it switches to [FireMode.SEQUENTIAL]).
     *
     * @param potatoes The list of HTTP requests to fire.
     * @return A list of [Result] objects representing the responses.
     * @since 0.1.0
     */
    fun fireWithResults(potatoes: List<FireablePotato>): List<Result> {
        val useGlobal = settings.asSequence().filterIsInstance<UseGlobalContext>().lastOrNull()
        val useSession = settings.lastSettingWithDefault<UseSessionContext>(UseSessionContext())

        val ctx = CompositeContext(useSession.ctx, useGlobal?.ctx)

        val usedPotatoes: List<Potato> = potatoes.flatMap { p ->
            when (p) {
                is PotatoFromContext -> {
                    try {
                        p.resolve(ctx)
                    } catch (t: Throwable) {
                        throw RequestPreparationException("Failed to resolve PotatoFromContext", t)
                    }
                }
                is Potato -> listOf(p)
            }
        }

        val configuredMode = settings.lastSettingWithDefault<FireMode>(FireMode.PARALLEL)
        val pacing = settings.lastSettingWithDefault<Pacing>(Pacing(0))
        val mode = if (!pacing.isZeroPacing) FireMode.SEQUENTIAL else configuredMode

        val results = Collections.synchronizedList(mutableListOf<Result>())

        if (mode == FireMode.SEQUENTIAL) {
            var first = true

            usedPotatoes.forEach { potato ->
                if (!first && !pacing.isZeroPacing) Thread.sleep(pacing.intervalMillis(ctx))
                first = false

                val effSettings = effectiveSettings(settings, potato.settings, ctx)
                results += runPotatoWithRetries(potato, ctx, effSettings)

            }
            return results.toList()
        }

        // PARALLEL
        val maxConcurrent =
            settings.lastSettingWithDefault<ConcurrencyLimit>(ConcurrencyLimit(ConcurrencyLimit.DEFAULT)).value
        val permits = Semaphore(maxConcurrent, false)
        val pool: ExecutorService = ParallelExecutorService.taskExecutor()

        try {
            val tasks = usedPotatoes.map { potato ->
                Callable {
                    withPermit(permits) {
                        val effSettings = effectiveSettings(settings, potato.settings, ctx)
                        runPotatoWithRetries(potato, ctx, effSettings)
                    }
                }
            }

            val futures = pool.invokeAll(tasks)
            futures.forEach { f ->
                try {
                    results += f.get()
                } catch (t: Throwable) {
                    val cause = t.cause ?: t
                    when (cause) {
                        is PotatoCannonException -> throw cause
                        is AssertionError -> throw cause
                        else -> throw RequestExecutionException(cause)
                    }
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.MINUTES)
        }

        return results.toList()
    }

    private inline fun <T> withPermit(sem: Semaphore, block: () -> T): T {
        sem.acquire()
        try {
            return block()
        } finally {
            sem.release()
        }
    }

    private fun runPotatoWithRetries(
        potato: Potato,
        ctx: CompositeContext,
        settings: List<PotatoCannonSetting>
    ): Result {
        var attempt = 0
        val retryLimit = settings.lastSettingWithDefault<RetryLimit>(RetryLimit(RetryLimit.DEFAULT)).count
        val retryPolicy = settings.lastSettingWithDefault<RetryDelay>(RetryDelay(RetryDelayPolicy.PROGRESSIVE))
        while (true) {
            try {
                return fireOne(potato, attempt, ctx, settings)
            } catch (t: Throwable) {
                if (t is RequestSendingFailureException && attempt < retryLimit) {
                    val backOffTime = backoff(attempt++, ctx, retryPolicy)
                    if (backOffTime == 0L) {
                        continue
                    }
                    Thread.sleep(backOffTime)
                } else {
                    when (t) {
                        is PotatoCannonException -> throw t
                        is AssertionError -> throw t
                        else -> throw RequestExecutionException(t)
                    }
                }
            }
        }
    }


    private fun backoff(attempt: Int, ctx: ContextView, backoffStrategy: RetryDelay): Long {
        return backoffStrategy.delayMillis(attempt, ctx)
    }

    private fun fireOne(
        potato: Potato,
        currentAttempt: Int,
        context: CompositeContext,
        allSettings: List<PotatoCannonSetting>
    ): Result {

        val allQueryParams = mutableMapOf<String, List<String>>()

        allSettings
            .filterIsInstance<QueryParam>()
            .forEach { it.apply(allQueryParams) }

        val queryString = if (allQueryParams.isNotEmpty()) {
            "?" + allQueryParams.entries.joinToString("&") { entry ->
                entry.value.joinToString("&") { value ->
                    "${URLEncoder.encode(entry.key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                }
            }
        } else {
            ""
        }

        val urlToUse = allSettings
            .filterIsInstance<OverrideBaseUrl>()
            .lastOrNull()?.url ?: baseUrl

        val fullUrl = urlToUse + potato.path + queryString

        val allHeaders = mutableMapOf<String, List<String>>()

        (allSettings + userAgentStrategy())
            .filterIsInstance<HeaderStrategy>()
            .forEach { it.apply(allHeaders) }

        val builder = SimpleRequestBuilder.create(potato.method.name)
        builder.uri = validUri(fullUrl)

        allHeaders.forEach { (key, values) ->
            values.forEach { value ->
                val (k, v) = validHeader(key, value)
                builder.addHeader(k, v)
            }
        }

        fun findHeaderValue(headers: Map<String, List<String>>, name: String): String? {
            val key = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
            return headers[key]?.lastOrNull()
        }

        val version = allSettings.lastSettingWithDefault<HttpProtocolVersion>(HttpProtocolVersion.NEGOTIATE)

        val client = when (version) {
            HttpProtocolVersion.HTTP_1_1 -> h1Client
            HttpProtocolVersion.HTTP_2 -> h2Client
            HttpProtocolVersion.NEGOTIATE -> negotiateClient
        }

        val body = potato.body

        val bodyToUse: ConcretePotatoBody? = when (body) {
            is BodyFromContext -> {
                try {
                    body.content(context)
                } catch (t: Throwable) {
                    throw RequestPreparationException("Failed to resolve BodyFromContext", t)
                }
            }

            is TextPotatoBody -> body
            is BinaryPotatoBody -> body
            is BodyFromObject<*> -> {
                val obj = body.obj
                try {
                    body.resolve()
                } catch (t: Throwable) {
                    throw RequestPreparationException("Failed to resolve BodyFromObject for object: $obj", t)
                }

            }

            null -> null
        }

        when (bodyToUse) {
            is TextPotatoBody -> {
                builder.setBody(bodyToUse.content, null)

                if (bodyToUse.includeCharset) {
                    val currentContentType = findHeaderValue(allHeaders, "Content-Type")
                        ?: throw RequestPreparationException(
                            "includeCharset is true but no Content-Type header is present; " +
                                    "set an explicit media type (e.g., text/plain) before enabling includeCharset.",
                            null
                        )

                    val amended = if (currentContentType.contains("charset=", ignoreCase = true)) {
                        currentContentType
                    } else {
                        "$currentContentType; charset=${bodyToUse.charset.name()}"
                    }

                    builder.setHeader("Content-Type", amended) // overwrite
                }
            }

            is BinaryPotatoBody -> {
                builder.setBody(bodyToUse.content, null)
            }

            null -> Unit
        }


        if (potato.method == HttpMethod.TRACE && body != null) {
            throw RequestPreparationException(
                "TRACE requests must not include a request body",
                null
            )
        }

        val baseLogging = allSettings.lastSettingWithDefault<Logging>(Logging.FULL)

        val logExcludes = allSettings
            .filterIsInstance<LogExclude>()
            .toSet()

        val expectations = allSettings
            .filterIsInstance<Expectation>() + allSettings
            .filterIsInstance<Check>()
            .map { Expectation(it) }

        val timeout =
            allSettings.lastSettingWithDefault<RequestTimeout>(RequestTimeout(RequestTimeout.DEFAULT)).durationMillis

        val ctx = HttpClientContext.create()

        val wireResponse = try {
            val t0 = System.nanoTime()

            val resp: SimpleHttpResponse = client
                .execute(builder.build(), ctx, null)
                .getWithin(timeout)

            val bodyBytes = resp.bodyBytes ?: ByteArray(0)
            val tDone = System.nanoTime()

            val wireMs = (tDone - t0) / 1_000_000

            val respHeaders: Map<String, List<String>> =
                resp.headers.groupBy({ it.name.lowercase() }, { it.value })

            WireResponse(
                statusCode = resp.code,
                headers = respHeaders,
                body = bodyBytes,
                wireDurationMillis = wireMs,
                httpVersion = ctx.protocolVersion.toNegotiatedProtocol()
            )
        } catch (t: Throwable) {
            throw RequestSendingFailureException(
                "Failed to send ${potato.method} request to $fullUrl within ${currentAttempt + 1} attempts: ${t.message}",
                t
            )
        }

        val finalRequestHeaders: Map<String, List<String>> =
            (ctx.getAttribute(PC_FINAL_REQ_HDRS) as? Map<String, List<String>>) ?: emptyMap()

        val deserializationStrategies = allSettings
            .filterIsInstance<DeserializationStrategy>()

        val result = Result(
            potato = potato,
            fullUrl = fullUrl,
            statusCode = wireResponse.statusCode,
            requestBody = bodyToUse,
            responseBody = wireResponse.body,
            responseHeaders = Headers(wireResponse.headers),
            requestHeaders = Headers(finalRequestHeaders),
            durationMillis = wireResponse.wireDurationMillis,
            queryParams = allQueryParams,
            deserializationStrategies = deserializationStrategies,
            attempts = currentAttempt + 1,
            protocol = wireResponse.httpVersion
        )

        allSettings
            .filterIsInstance<CaptureToContext>()
            .forEach {
                context.set(it.key, it.target, it.fn(result, context))
            }

        val expectationResults = expectations
            .map { it.verify(result) }

        val logCommentary = allSettings
            .filterIsInstance<LogCommentary>()

        result.log(baseLogging, logExcludes, expectationResults, logCommentary)

        expectationResults
            .filter { it.error != null }
            .forEach { throw it.error!! }

        return result
    }
}

data class WireResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val wireDurationMillis: Long,
    val httpVersion: NegotiatedProtocol
)

private fun ProtocolVersion?.toNegotiatedProtocol(): NegotiatedProtocol {
    if (this == null) return NegotiatedProtocol("unknown", null, null, ProtocolFamily.OTHER)
    val token = toString()              // "HTTP/1.1", "HTTP/2.0", etc.
    val family = when {
        protocol.equals("HTTP", ignoreCase = true) && major == 1 && minor == 0 -> ProtocolFamily.HTTP_1_0
        protocol.equals("HTTP", ignoreCase = true) && major == 1 -> ProtocolFamily.HTTP_1_1
        protocol.equals("HTTP", ignoreCase = true) && major == 2 -> ProtocolFamily.HTTP_2
        protocol.equals("HTTP", ignoreCase = true) && major == 3 -> ProtocolFamily.HTTP_3
        else -> ProtocolFamily.OTHER
    }
    return NegotiatedProtocol(token, major, minor, family)
}

private fun effectiveSettings(
    cannonSettings: List<CannonSetting>,
    potatoSettings: List<PotatoSetting>,
    context: ContextView
): List<PotatoCannonSetting> {
    return (cannonSettings + potatoSettings)
        .map {
            when (it) {
                is ResolveFromContext -> it.materialize(context) ?: NoOp
                else -> it
            }
        }
}

private object Deadlines {
    val scheduler = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "PotatoCannon-Deadline").apply { isDaemon = true }
    }
}

private fun <T> Future<T>.getWithin(timeoutMs: Long): T {
    val cancelTask = Deadlines.scheduler.schedule({ cancel(true) }, timeoutMs, TimeUnit.MILLISECONDS)
    return try {
        get().also { cancelTask.cancel(false) } // success before deadline
    } catch (e: CancellationException) {
        throw TimeoutException("Timed out after $timeoutMs ms").apply { initCause(e) }
    } finally {
        cancelTask.cancel(false)
    }
}