package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.Result
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.PotatoBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.CannonConfiguration
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.LoggingStrategy
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.Executors



class Cannon {
    val baseUrl: String
    val configuration: List<CannonConfiguration>
    private val client: HttpClient

    constructor(baseUrl: String) : this(baseUrl, listOf())

    constructor(baseUrl: String, configuration: List<CannonConfiguration>) {
        this.baseUrl = baseUrl
        this.configuration = configuration
        this.client = HttpClient.newHttpClient()

    }

    fun fire(vararg potatoes: Potato) {
        fire(potatoes.toList())
    }

    fun fire(potatoes: List<Potato>) {
        val mode = configuration
            .filterIsInstance<FireMode>()
            .firstOrNull()?.mode ?: Mode.Sequential

        when (mode) {
            Mode.Sequential -> potatoes.forEach { fireOne(it) }
            Mode.Parallel -> fireParallel(potatoes)
        }
    }

    fun fireParallel(potatoes: List<Potato>) {
        val pool = Executors.newFixedThreadPool(500)
        val futures = potatoes.map {
            pool.submit { fireOne(it) }
        }
        futures.forEach { it.get() }
        pool.shutdown()
    }

    private fun fireOne(potato: Potato) {

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

        val loggingStrategy = configs
            .filterIsInstance<LoggingStrategy>()
            .lastOrNull() ?: LoggingStrategy.FULL

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

            result.log(loggingStrategy)

            configs
                .filterIsInstance<ResultVerification>()
                .forEach { it.verify(result) }

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

            result.log(loggingStrategy)

            configs
                .filterIsInstance<ResultVerification>()
                .forEach { it.verify(result) }
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