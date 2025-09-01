package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.exception.ExecutionFailureException
import io.github.boomkartoffel.potatocannon.exception.PotatoCannonException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingException
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.Headers
import io.github.boomkartoffel.potatocannon.result.log
import io.github.boomkartoffel.potatocannon.strategy.OverrideBaseUrl
import io.github.boomkartoffel.potatocannon.strategy.CannonConfiguration
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.ConcurrencyLimit
import io.github.boomkartoffel.potatocannon.strategy.LogCommentary
import io.github.boomkartoffel.potatocannon.strategy.RetryLimit
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


/**
 * A configurable HTTP test runner that fires predefined requests ("Potatoes") against a target base URL.
 *
 * The `Cannon` supports sequential and parallel execution modes, custom headers, logging, expectations,
 * and other reusable configuration strategies. Designed for functional testing, stress testing, or
 * automation of REST-like APIs.
 *
 * @property baseUrl The root URL used to resolve all relative request paths.
 * @property configuration A list of reusable strategies or behaviors that affect request firing.
 * @since 0.1.0
 */
class Cannon {
    private val baseUrl: String
    private val configuration: List<CannonConfiguration>
    private val client: HttpClient

    constructor(baseUrl: String) : this(baseUrl, listOf())

    constructor(baseUrl: String, vararg configuration: CannonConfiguration) : this(baseUrl, configuration.toList())

    constructor(baseUrl: String, configuration: List<CannonConfiguration>) {
        this.baseUrl = baseUrl
        this.configuration = configuration
        this.client = HttpClient.newHttpClient()
    }

    fun withFireMode(mode: FireMode): Cannon {
        return addConfiguration(mode)
    }

    /**
     * Returns a new `Cannon` instance with additional configuration strategies appended.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined configuration.
     *
     * @param additionalConfiguration The new configuration strategies to add.
     * @return A new `Cannon` with extended configuration.
     * @since 0.1.0
     */
    fun addConfiguration(vararg additionalConfiguration: CannonConfiguration): Cannon =
        this.addConfiguration(additionalConfiguration.toList())

    /**
     * Returns a new `Cannon` instance with additional configuration strategies appended. This means that strategies like [FireMode], which resolve to the last one trumping the previous ones, will be applied.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined configuration.
     *
     * @param additionalConfiguration The new configuration strategies to add.
     * @return A new `Cannon` with extended configuration.
     * @since 0.1.0
     */
    fun addConfiguration(additionalConfiguration: List<CannonConfiguration>): Cannon =
        Cannon(baseUrl, configuration + additionalConfiguration)

    /**
     * Fires the givens single or multiple [Potato].
     *
     * If no [FireMode] is specified, the default is [FireMode.PARALLEL].
     *
     * @param potatoes The HTTP requests to fire.
     * @return A list of `Result` objects representing the responses.
     * @since 0.1.0
     */
    fun fire(vararg potatoes: Potato): List<Result> {
        return fire(potatoes.toList())
    }

    /**
     * Fires a list of [Potato] requests according to the configured `FireMode`.
     *
     * If no `FireMode` is specified, the default is `PARALLEL`.
     *
     * @param potatoes The list of HTTP requests to fire.
     * @return A list of `Result` objects representing the responses.
     * @since 0.1.0
     */
    fun fire(potatoes: List<Potato>): List<Result> {
        val mode = configuration
            .filterIsInstance<FireMode>()
            .lastOrNull() ?: FireMode.PARALLEL

        val isSequentialFiring = (mode == FireMode.SEQUENTIAL)
        val isParallelFiring = !isSequentialFiring

        var maxConcurrent = configuration
            .filterIsInstance<ConcurrencyLimit>()
            .lastOrNull()
            ?.value ?: ConcurrencyLimit.DEFAULT

        if (isSequentialFiring) maxConcurrent = 1

        val results = Collections.synchronizedList(mutableListOf<Result>())

        val permits = Semaphore(maxConcurrent, true)
        val pool: ExecutorService = ParallelExecutorService.taskExecutor()

        try {
            val futures = potatoes.map { potato ->
                pool.submit {
                    var attempt = 0
                    var isPermitAcquired = false

                    val retryLimit = (configuration + potato.configuration)
                        .filterIsInstance<RetryLimit>()
                        .lastOrNull()
                        ?.count ?: RetryLimit.DEFAULT

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
                                if (t is RequestSendingException && attempt < retryLimit) {
                                    retryDelayMs = backoff(attempt++) // compute delay
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
                        else -> throw ExecutionFailureException(cause)
                    }
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.MINUTES)
        }

        return results.toList()
    }

    private fun backoff(attempt: Int): Long {
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

        val configs = configuration + potato.configuration

        configs
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

        val urlToUse = potato.configuration
            .filterIsInstance<OverrideBaseUrl>()
            .lastOrNull()?.url ?: baseUrl

        val fullUrl = urlToUse + potato.path + queryString

        val allHeaders = mutableMapOf<String, List<String>>()

        configs
            .filterIsInstance<HeaderStrategy>()
            .forEach { it.apply(allHeaders) }

        val builder = try {
            HttpRequest.newBuilder().uri(URI.create(fullUrl))
        } catch (t: Throwable) {
            throw ExecutionFailureException(t)
        }

        allHeaders.forEach { (key, values) ->
            values.forEach { value ->
                try {
                    builder.header(key, value)
                } catch (t: Throwable) {
                    throw ExecutionFailureException(t)
                }
            }

        }

        val timeout = configs
            .filterIsInstance<RequestTimeout>()
            .lastOrNull()?.durationMillis ?: RequestTimeout.DEFAULT

        builder.timeout(Duration.ofMillis(timeout))

        val request = when (val body = potato.body) {
            is TextBody -> builder.method(potato.method.name, BodyPublishers.ofString(body.content)).build()
            is BinaryBody -> builder.method(potato.method.name, BodyPublishers.ofByteArray(body.content)).build()
            null -> builder.method(potato.method.name, BodyPublishers.noBody()).build()
        }

        val baseLogging = configs
            .filterIsInstance<Logging>()
            .lastOrNull() ?: Logging.FULL

        val logExcludes = configs
            .filterIsInstance<LogExclude>()
            .toSet()

        val expectations = configs
            .filterIsInstance<Expectation>() + configs
            .filterIsInstance<Check>()
            .map { Expectation(it) }


        val start = System.currentTimeMillis()
        val response = try {
            client.send(request, BodyHandlers.ofByteArray())
        } catch (t: Throwable) {
            throw RequestSendingException(t, currentAttempt + 1)
        }
        val duration = System.currentTimeMillis() - start

        val deserializationStrategies = configs
            .filterIsInstance<DeserializationStrategy>()

        val result = Result(
            potato = potato,
            fullUrl = fullUrl,
            statusCode = response.statusCode(),
            responseBody = response.body().takeIf { it.isNotEmpty() },
            responseHeaders = Headers(response.headers().map().mapKeys { it.key.lowercase() }),
            requestHeaders = Headers(request.headers().map().mapKeys { it.key.lowercase() }),
            durationMillis = duration,
            queryParams = allQueryParams,
            deserializationStrategies = deserializationStrategies,
            attempts = currentAttempt + 1
        )

        val expectationResults = expectations
            .map { it.verify(result) }

        val logCommentary = configs
            .filterIsInstance<LogCommentary>()

        result.log(baseLogging, logExcludes, expectationResults, logCommentary)

        expectationResults
            .filter { it.error != null }
            .forEach { throw it.error!! }

        return result
    }
}