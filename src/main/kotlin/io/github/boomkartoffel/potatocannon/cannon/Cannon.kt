package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.Headers
import io.github.boomkartoffel.potatocannon.result.log
import io.github.boomkartoffel.potatocannon.strategy.CannonConfiguration
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.Collections
import java.util.concurrent.Executors
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

    /**
     * Returns a new `Cannon` instance with additional configuration strategies appended.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined configuration.
     *
     * @param additionalConfiguration The new configuration strategies to add.
     * @return A new `Cannon` with extended configuration.
     */
    fun withAmendedConfiguration(vararg additionalConfiguration: CannonConfiguration): Cannon = this.withAmendedConfiguration(additionalConfiguration.toList())

    /**
     * Returns a new `Cannon` instance with additional configuration strategies appended.
     *
     * This does not mutate the original `Cannon` but returns a new one with combined configuration.
     *
     * @param additionalConfiguration The new configuration strategies to add.
     * @return A new `Cannon` with extended configuration.
     */
    fun withAmendedConfiguration(additionalConfiguration: List<CannonConfiguration>): Cannon = Cannon(baseUrl, configuration + additionalConfiguration)

    /**
     * Fires the given `Potato` requests using vararg syntax.
     *
     * If no `FireMode` is specified, the default is `PARALLEL`.
     *
     * @param potatoes The HTTP requests to fire.
     * @return A list of `Result` objects representing the responses.
     */
    fun fire(vararg potatoes: Potato): List<Result> {
        return fire(potatoes.toList())
    }

    /**
     * Fires a list of `Potato` requests according to the configured `FireMode`.
     *
     * If no `FireMode` is specified, the default is `PARALLEL`.
     *
     * @param potatoes The list of HTTP requests to fire.
     * @return A list of `Result` objects representing the responses.
     */
    fun fire(potatoes: List<Potato>): List<Result> {
        val mode = configuration
            .filterIsInstance<FireMode>()
            .lastOrNull() ?: FireMode.PARALLEL

        return when (mode) {
            FireMode.SEQUENTIAL -> potatoes.map { fireOne(it) }
            FireMode.PARALLEL -> fireParallel(potatoes)
        }
    }

    private fun fireParallel(potatoes: List<Potato>): List<Result> {
        val results = Collections.synchronizedList(mutableListOf<Result>())
        val pool = Executors.newFixedThreadPool(500)

        try {
            val futures = potatoes.map { potato ->
                pool.submit<Result> {
                    val result = fireOne(potato)
                    results.add(result)
                    result
                }
            }

            futures.forEach { it.get() } // Ensure all tasks are completed
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }

        return results.toList()
    }

    /**
     * Fires a single `Potato` request and returns the result.
     *
     * This method constructs the full URL, applies query parameters and headers,
     * sends the request, and processes the response.
     *
     * @param potato The HTTP request to fire.
     * @return A `Result` object containing the response details.
     */
    fun fireOne(potato: Potato): Result {

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

        val fullUrl = baseUrl + potato.path + queryString

        val allHeaders = mutableMapOf<String, List<String>>()

        configs
            .filterIsInstance<HeaderStrategy>()
            .forEach { it.apply(allHeaders) }

        val builder = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))

        allHeaders.forEach { (key, values) ->
            values.forEach { value ->
                builder.header(key, value)
            }

        }

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

        val verifications = configs
            .filterIsInstance<ResultVerification>()

        val start = System.currentTimeMillis()

        val result = try {
            val response = client.send(request, BodyHandlers.ofByteArray())
            val duration = System.currentTimeMillis() - start

            Result(
                potato = potato,
                fullUrl = fullUrl,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = Headers(response.headers().map().mapKeys { it.key.lowercase() }),
                requestHeaders = Headers(request.headers().map().mapKeys { it.key.lowercase() }),
                durationMillis = duration,
                queryParams = allQueryParams,
                error = null
            )

        } catch (e: Exception) {
            Result(
                potato = potato,
                fullUrl = fullUrl,
                statusCode = -1,
                responseBody = null,
                responseHeaders = Headers(emptyMap()),
                durationMillis = System.currentTimeMillis() - start,
                requestHeaders = Headers(request.headers().map().mapKeys { it.key.lowercase() }),
                queryParams = allQueryParams,
                error = e
            )
        }

        result.log(baseLogging, logExcludes,verifications)

        verifications
            .forEach { it.verify(result) }

        return result
    }
}