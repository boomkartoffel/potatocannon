package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.exception.RequestExecutionException
import io.github.boomkartoffel.potatocannon.exception.PotatoCannonException
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingFailureException
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.Headers
import io.github.boomkartoffel.potatocannon.result.log
import io.github.boomkartoffel.potatocannon.strategy.OverrideBaseUrl
import io.github.boomkartoffel.potatocannon.strategy.CannonSetting
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.ConcurrencyLimit
import io.github.boomkartoffel.potatocannon.strategy.HttpProtocolVersion
import io.github.boomkartoffel.potatocannon.strategy.LogCommentary
import io.github.boomkartoffel.potatocannon.strategy.NegotiatedProtocol
import io.github.boomkartoffel.potatocannon.strategy.ProtocolFamily
import io.github.boomkartoffel.potatocannon.strategy.RetryLimit
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout
import io.github.boomkartoffel.potatocannon.strategy.RetryDelayPolicy
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.ProtocolVersion

import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.http2.HttpVersionPolicy
import userAgentStrategy
import validHeader
import validUri

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

    fun withFireMode(mode: FireMode): Cannon {
        return addSettings(mode)
    }

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
    fun fire(vararg potatoes: Potato): List<Result> {
        return fire(potatoes.toList())
    }

    /**
     * Fires a list of [Potato] requests according to the configured [FireMode].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL].
     *
     * @param potatoes The list of HTTP requests to fire.
     * @return A list of [Result] objects representing the responses.
     * @since 0.1.0
     */
    fun fire(potatoes: List<Potato>): List<Result> {
        val mode = settings
            .filterIsInstance<FireMode>()
            .lastOrNull() ?: FireMode.PARALLEL

        var maxConcurrent = settings
            .filterIsInstance<ConcurrencyLimit>()
            .lastOrNull()
            ?.value ?: ConcurrencyLimit.DEFAULT

        val isSequentialFiring = (mode == FireMode.SEQUENTIAL)
        val isParallelFiring = !isSequentialFiring

        if (isSequentialFiring) maxConcurrent = 1

        val results = Collections.synchronizedList(mutableListOf<Result>())

        val permits = Semaphore(maxConcurrent, false)
        val pool: ExecutorService = ParallelExecutorService.taskExecutor()

        try {
            val futures = potatoes.map { potato ->
                pool.submit {
                    var attempt = 0
                    var isPermitAcquired = false

                    val retryLimit = (settings + potato.settings)
                        .filterIsInstance<RetryLimit>()
                        .lastOrNull()
                        ?.count ?: RetryLimit.DEFAULT

                    val retryDelayPolicy = (settings + potato.settings)
                        .filterIsInstance<RetryDelayPolicy>()
                        .lastOrNull() ?: RetryDelayPolicy.PROGRESSIVE

                    try {
                        while (true) {
                            // acquire policy
                            if (isSequentialFiring) {
                                if (!isPermitAcquired) {
                                    permits.acquire();
                                    isPermitAcquired = true
                                }
                            } else {
                                permits.acquire()
                            }

                            var retryDelayMs: Long
                            try {
                                val r = fireOne(potato, attempt)

                                results.add(r)
                                return@submit
                            } catch (t: Throwable) {
                                if (t is RequestSendingFailureException && attempt < retryLimit) {
                                    retryDelayMs = backoff(attempt++, retryDelayPolicy) // compute delay
                                } else {
                                    throw t
                                }
                            } finally {
                                // in PARALLEL release per attempt; in SEQUENTIAL keep the single permit
                                if (isParallelFiring) permits.release()
                            }

                            // sleep after releasing (parallel) or while holding (sequential)
                            sleep(retryDelayMs)
                        }
                    } finally {
                        if (isSequentialFiring && isPermitAcquired) permits.release() // release the one held permit at the end
                    }
                }
            }

            futures.forEach { f ->
                try {
                    f.get()
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

    private fun backoff(attempt: Int, backoffStrategy: RetryDelayPolicy): Long {
        if (backoffStrategy == RetryDelayPolicy.NONE) {
            return 0
        }
        val steps = listOf(10L, 25L, 50L, 100L, 200L)
        return if (attempt < steps.size) steps[attempt] else steps.last() * (attempt - steps.size + 2)
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun fireOne(potato: Potato, currentAttempt: Int): Result {

        val allQueryParams = mutableMapOf<String, List<String>>()

        val allSettings = settings + potato.settings

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

        val urlToUse = potato.settings
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

        val version = allSettings
            .filterIsInstance<HttpProtocolVersion>()
            .lastOrNull() ?: HttpProtocolVersion.NEGOTIATE

        val client = when (version) {
            HttpProtocolVersion.HTTP_1_1 -> h1Client
            HttpProtocolVersion.HTTP_2 -> h2Client
            HttpProtocolVersion.NEGOTIATE -> negotiateClient
        }

        val body = potato.body

        when (body) {
            is TextBody -> {
                builder.setBody(body.content, null)

                if (body.includeCharset) {
                    val currentContentType = findHeaderValue(allHeaders, "Content-Type")
                        ?: throw RequestPreparationException(
                            "includeCharset is true but no Content-Type header is present; " +
                                    "set an explicit media type (e.g., text/plain) before enabling includeCharset.",
                            null
                        )

                    val amended = if (currentContentType.contains("charset=", ignoreCase = true)) {
                        currentContentType
                    } else {
                        "$currentContentType; charset=${body.charset.name()}"
                    }

                    builder.setHeader("Content-Type", amended) // overwrite
                }
            }

            is BinaryBody -> {
                builder.setBody(body.content, null)
            }

            null -> Unit
        }


        if (potato.method == HttpMethod.TRACE && body != null) {
            throw RequestPreparationException(
                "TRACE requests must not include a request body",
                null
            )
        }

        val baseLogging = allSettings
            .filterIsInstance<Logging>()
            .lastOrNull() ?: Logging.FULL

        val logExcludes = allSettings
            .filterIsInstance<LogExclude>()
            .toSet()

        val expectations = allSettings
            .filterIsInstance<Expectation>() + allSettings
            .filterIsInstance<Check>()
            .map { Expectation(it) }

        val timeout = allSettings
            .filterIsInstance<RequestTimeout>()
            .lastOrNull()?.durationMillis ?: RequestTimeout.DEFAULT

        val ctx = HttpClientContext.create()


        val wireResponse = try {
            val t0 = System.nanoTime()

            val resp: SimpleHttpResponse = client
                .execute(builder.build(), ctx, null)
                .get(timeout, TimeUnit.MILLISECONDS)

            val bodyBytes = resp.bodyBytes ?: ByteArray(0)
            val tDone = System.nanoTime()

            val respHeaders: Map<String, List<String>> =
                resp.headers.groupBy({ it.name.lowercase() }, { it.value })

            WireResponse(
                statusCode = resp.code,
                headers = respHeaders,
                body = bodyBytes,
                wireDurationMillis = (tDone - t0) / 1_000_000,
                httpVersion = ctx.protocolVersion.toNegotiatedProtocol()
            )
        } catch (t: Throwable) {
            throw RequestSendingFailureException(t, currentAttempt + 1, potato.method, fullUrl)
        }

        val finalRequestHeaders: Map<String, List<String>> =
            (ctx.getAttribute(PC_FINAL_REQ_HDRS) as? Map<String, List<String>>) ?: emptyMap()

        val deserializationStrategies = allSettings
            .filterIsInstance<DeserializationStrategy>()

        val result = Result(
            potato = potato,
            fullUrl = fullUrl,
            statusCode = wireResponse.statusCode,
            responseBody = wireResponse.body,
            responseHeaders = Headers(wireResponse.headers),
            requestHeaders = Headers(finalRequestHeaders),
            durationMillis = wireResponse.wireDurationMillis,
            queryParams = allQueryParams,
            deserializationStrategies = deserializationStrategies,
            attempts = currentAttempt + 1,
            protocol = wireResponse.httpVersion
        )

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