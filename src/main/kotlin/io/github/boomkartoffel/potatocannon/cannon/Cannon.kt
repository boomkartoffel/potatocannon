package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.Result
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.PotatoBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.CannonConfiguration
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


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

    fun fire(vararg potatoes: Potato): List<Result> {
        return fire(potatoes.toList())
    }

    fun fire(potatoes: List<Potato>): List<Result> {
        val mode = configuration
            .filterIsInstance<FireMode>()
            .firstOrNull()?.mode ?: Mode.Sequential

        return when (mode) {
            Mode.Sequential -> potatoes.map { fireOne(it) }
            Mode.Parallel -> fireParallel(potatoes)
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

    private fun fireOne(potato: Potato): Result {

        val allQueryParams = mutableMapOf<String, List<String>>()

        val configs = configuration + potato.configuration

        configs
            .filterIsInstance<QueryParam>()
            .forEach { it.apply(allQueryParams) }

        val queryString = if (allQueryParams.isNotEmpty()) {
            "?" + allQueryParams.entries.joinToString("&") { entry ->
                entry.value.joinToString("&") { value -> "${entry.key}=$value" }
            }
        } else {
            ""
        }

        val fullUrl = baseUrl + potato.path + queryString

        val allHeaders = mutableMapOf<String, String>()

        configs
            .filterIsInstance<HeaderStrategy>()
            .forEach { it.apply(allHeaders) }

        val builder = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))

        allHeaders.forEach { (key, value) ->
            builder.header(key, value)
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

        val start = System.currentTimeMillis()

        try {
            val response = client.send(request, BodyHandlers.ofByteArray())
            val duration = System.currentTimeMillis() - start

            val result = Result(
                potato = potato,
                fullUrl = fullUrl,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                requestHeaders = request.headers().map(),
                durationMillis = duration,
                queryParams = allQueryParams,
                error = null
            )

            result.log(baseLogging, logExcludes)

            configs
                .filterIsInstance<ResultVerification>()
                .forEach { it.verify(result) }

            return result

        } catch (e: Exception) {
            val result = Result(
                potato = potato,
                fullUrl = fullUrl,
                statusCode = -1,
                responseBody = null,
                responseHeaders = emptyMap(),
                durationMillis = System.currentTimeMillis() - start,
                requestHeaders = request.headers().map(),
                queryParams = allQueryParams,
                error = e
            )

            result.log(baseLogging, logExcludes)

            configs
                .filterIsInstance<ResultVerification>()
                .forEach { it.verify(result) }

            return result
        }
    }
}

private fun ByteArray.toPotatoBody(): PotatoBody? {
    if (isEmpty()) return null

    val charset = Charsets.UTF_8
    val text = try {
        val decoded = String(this, charset)
        // Heuristic: check if decoded string has mostly printable characters
        val printableRatio = decoded.count { it.isLetterOrDigit() || it.isWhitespace() || it.isLetter() || it in ' '..'~' }.toDouble() / decoded.length
        if (printableRatio > 0.9) {
            TextBody(decoded)
        } else {
            BinaryBody(this)
        }
    } catch (e: Exception) {
        BinaryBody(this)
    }

    return text
}

enum class Mode { Sequential, Parallel }