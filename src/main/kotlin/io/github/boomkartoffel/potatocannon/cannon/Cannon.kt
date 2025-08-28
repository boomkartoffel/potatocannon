package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.exception.ExecutionFailureException
import io.github.boomkartoffel.potatocannon.exception.PotatoCannonException
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
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.Collections
import java.util.concurrent.ExecutionException
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
                    results.add(result) // ensure 'results' is thread-safe!
                    result
                }
            }

            for (f in futures) {
                try {
                    f.get() // wait; will throw ExecutionException on failure inside task
                } catch (ee: ExecutionException) {
                    val cause = ee.cause
                    when (cause) {
                        is PotatoCannonException -> throw cause
                        else -> throw ExecutionFailureException(cause)
                    }
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }

        return results.toList()
    }

    /**
     * Fires a single [Potato] request and returns the result.
     *
     * This method constructs the full URL, applies query parameters and headers,
     * sends the request, and processes the response.
     *
     * @param potato The [Potato] to fire.
     * @return A [Result] object containing the response details.
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

        val urlToUse = potato.configuration
            .filterIsInstance<OverrideBaseUrl>()
            .lastOrNull()?.url ?: baseUrl

        val fullUrl = urlToUse + potato.path + queryString

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

        val expectations = configs
            .filterIsInstance<Expectation>() + configs
            .filterIsInstance<Check>()
            .map { Expectation(it) }

        val start = System.currentTimeMillis()

        val deserializationStrategies = configs
            .filterIsInstance<DeserializationStrategy>()

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
                deserializationStrategies = deserializationStrategies,
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
                deserializationStrategies = deserializationStrategies,
                error = e
            )
        }


        val expectationResults = expectations
            .map { it.verify(result) }

        result.log(baseLogging, logExcludes, expectationResults)

        val failures = expectationResults.filter { it.error != null }
        if (failures.isNotEmpty()) {
            // Optional: add a single-line summary to your *log* builder here if you want:
            // builder.appendLine("|      ✘ ${failures.size} verification(s) failed")

            // Build *only* the detailed items. Do NOT repeat the "N verifications failed:" header
            val details = failures.joinToString("\n\n") { vr ->
                val desc = vr.expectation.description.ifBlank { "unnamed verification" }
                val msg = vr.error?.message?.trim().orEmpty()
                buildString {
                    appendLine("$desc:")
                    if (msg.isNotEmpty()) appendLine(msg)
                }.trimEnd()
            }

//            println(details)

            // Throw with detailed items only; the runner will prefix with "AssertionError: ..."
            failures.forEach { throw it.error!! }
        }


        return result
    }
}