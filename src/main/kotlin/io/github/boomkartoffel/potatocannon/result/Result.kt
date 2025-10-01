package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.JsonPathDecodingException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.marshalling.Deserializer
import io.github.boomkartoffel.potatocannon.marshalling.JsonDeserializer
import io.github.boomkartoffel.potatocannon.marshalling.WireFormat
import io.github.boomkartoffel.potatocannon.marshalling.XmlDeserializer
import io.github.boomkartoffel.potatocannon.potato.ConcretePotatoBody
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import io.github.boomkartoffel.potatocannon.strategy.NegotiatedProtocol
import java.nio.charset.Charset
import com.fasterxml.jackson.databind.JsonNode as JacksonNode

private val defaultCharset = Charsets.UTF_8
private val defaultFormat = WireFormat.JSON
private const val contentTypeHeaderName = "content-type"

private val JSON_MAPPER = jacksonObjectMapper()

/**
 * Immutable view of HTTP headers with case-insensitive lookup.
 *
 * Header names are normalized to lowercase on construction, so
 * retrieval with [get] does not depend on the original casing.
 *
 * Typical use cases:
 * - Accessing response headers returned by the server
 * - Inspecting request headers after they have been applied
 *
 * @since 0.1.0
 */
class Headers internal constructor(rawHeaders: Map<String, List<String>>) {

    private val normalized = rawHeaders.mapKeys { it.key.lowercase() }

    operator fun get(key: String): List<String> {
        return normalized[key.lowercase()] ?: emptyList()
    }

    internal fun toMap(): Map<String, List<String>> = normalized
}

/**
 * Represents the outcome of firing a single [Potato] request.
 *
 * Contains full details about the request, response, infos about the request execution, the used [Potato], and helper methods.
 *
 * @property potato The original [Potato] that was fired.
 * @property fullUrl The full URL used in the request, including query parameters.
 * @property statusCode The HTTP response status code (e.g., 200, 404).
 * @property requestBody The [ConcretePotatoBody] sent with the request, or `null` if no body was sent.
 * @property responseBody The raw response body as a [ByteArray]
 * @property requestHeaders The [Headers] sent with the request.
 * @property responseHeaders The [Headers] received in the response.
 * @property queryParams The query parameters used in the request.
 * @property durationMillis Total time in milliseconds taken to execute the request and receive the response.
 * @property attempts The number of attempts it took to successfully send the request.
 * @since 0.1.0
 */
class Result internal constructor(
    val potato: Potato,
    val fullUrl: String,
    val statusCode: Int,
    val requestBody: ConcretePotatoBody?,
    val responseBody: ByteArray,
    val requestHeaders: Headers,
    val responseHeaders: Headers,
    val queryParams: Map<String, List<String>>,
    val durationMillis: Long,
    //this is not a configuration, but a list of strategies that are necessary for deserialization, and it is not supposed to be accessed by the user
    internal val deserializationStrategies: List<DeserializationStrategy>,
    val attempts: Int,
    val protocol: NegotiatedProtocol,
) {

    /**
     * Decodes the raw response body into text using the given [charset].
     *
     * @param charset The character set to use when decoding the body.
     * @return The decoded response text, or an empty String if the response has no body.
     * @since 0.1.0
     */
    fun responseText(charset: Charset) = responseBody.toString(charset)

    /**
     * Decodes the raw response body into text by automatic detection.
     *
     * The charset is typically resolved from the `Content-Type` response header or defaults to UTF-8.
     *
     * @return The decoded response text, or an empty String if the response has no body.
     * @since 0.1.0
     */
    fun responseText() = responseText(responseCharset())

    /**
     * Deserializes the response body into one instance of [T] using the **default format (JSON)**
     * and the detected response charset.
     *
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>): T = bodyAsObject(clazz, defaultFormat)

    /**
     * Deserializes the response body into one instance of [T] using the **default format (JSON)**
     * and the specified [charset] to decode the body text.
     *
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, charset: Charset): T = bodyAsObject(clazz, defaultFormat, charset)

    /**
     * Deserializes the response body into one instance of [T] using the given [format]
     * and the detected response charset.
     *
     * @param format The wire format to use (e.g., JSON or XML).
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, format: WireFormat): T =
        bodyAsObject(clazz, format, responseCharset())

    /**
     * Deserializes the response body into one instance of [T] using the provided [deserializer]
     * and the detected response charset.
     *
     * @param deserializer A custom [io.github.boomkartoffel.potatocannon.marshalling.Deserializer] implementation.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, deserializer: Deserializer): T =
        bodyAsObject(clazz, deserializer, responseCharset())

    /**
     * Deserializes the response body into one instance of [T] using the given [format]
     * and [charset]. The underlying deserializer is chosen based on the [WireFormat] and will
     * apply any configured [DeserializationStrategy].
     *
     * @param format The wire format to use (e.g., JSON or XML).
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, format: WireFormat, charset: Charset): T {
        val deserializer = when (format) {
            WireFormat.JSON -> JsonDeserializer(deserializationStrategies)
            WireFormat.XML -> XmlDeserializer(deserializationStrategies)
        }

        return bodyAsObject(clazz, deserializer, charset)
    }


    /**
     * Deserializes the response body into a list of [T] using the default wire format
     * and the detected response charset.
     *
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>): List<T> = bodyAsList(clazz, defaultFormat, responseCharset())


    /**
     * Deserializes the response body into a list of [T] using the default wire format
     * and the specified [charset] to decode the body text.
     *
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, charset: Charset): List<T> =
        bodyAsList(clazz, defaultFormat, charset)


    /**
     * Deserializes the response body into a list of [T] using the given [format]
     * and the detected response charset.
     *
     * @param format The [WireFormat] to use (e.g., JSON or XML).
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, format: WireFormat): List<T> =
        bodyAsList(clazz, format, responseCharset())


    /**
     * Deserializes the response body into a list of [T] using the provided [deserializer]
     * and the detected response charset.
     *
     * @param deserializer A custom [Deserializer] implementation.
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, deserializer: Deserializer): List<T> =
        bodyAsList(clazz, deserializer, responseCharset())

    /**
     * Deserializes the response body into a list of [T] using the given [format]
     * and [charset]. The underlying deserializer is chosen based on [format] and will
     * apply any configured [DeserializationStrategy].
     *
     * @param format The wire format to use (e.g., JSON or XML).
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, format: WireFormat, charset: Charset): List<T> {
        val deserializer = when (format) {
            WireFormat.JSON -> JsonDeserializer(deserializationStrategies)
            WireFormat.XML -> XmlDeserializer(deserializationStrategies)
        }

        return bodyAsList(clazz, deserializer, charset)
    }


    /**
     * Deserializes the response body (decoded with [charset]) into one instance of [T]
     * using the supplied [deserializer].
     *
     * @param deserializer The deserializer to use.
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, deserializer: Deserializer, charset: Charset): T {
        val text = responseText(charset)
        if (text.isEmpty()) {
            throw ResponseBodyMissingException()
        }
        return try {
            deserializer.deserializeObject(text, clazz)
        } catch (e: Exception) {
            throw DeserializationFailureException(clazz.name, e)
        }
    }

    /**
     * Deserializes the response body (decoded with [charset]) into a list of [T]
     * using the supplied [deserializer].
     *
     * @param deserializer The deserializer to use.
     * @param charset [Charset] used to decode the response body prior to parsing.
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, deserializer: Deserializer, charset: Charset): List<T> {
        val text = responseText(charset)
        if (text.isEmpty()) {
            throw ResponseBodyMissingException()
        }
        return try {
            deserializer.deserializeList(text, clazz)
        } catch (e: Exception) {
            throw DeserializationFailureException(clazz.name, e)
        }
    }

    private fun responseCharset(): Charset {
        return responseHeaders[contentTypeHeaderName]
            .firstOrNull()
            ?.let { extractCharset(it) }
            ?: defaultCharset
    }

    /**
     * Evaluates a JSONPath expression (Jayway) against this response’s JSON body and returns
     * a strict, fail-fast wrapper over the selected node.
     *
     * The underlying implementation is [Jayway JSONPath (2.9.0)](https://github.com/json-path/JsonPath).
     *
     * ### Behavior
     * - Parses the response body as JSON, then evaluates the given JSONPath.
     * - The raw JSONPath result (which may be a `scalar`, `List`, or `Map`) is normalized as a [PathMatch].
     * - [PathMatch] is **strict**: it throws when the node is missing, or when accessing the data doesn't match the expected type (e.g., `"true"` is not a JSON boolean).
     * - JSONPath **filters** and **wildcards** commonly produce arrays; index into them (e.g. `[0]`) or call
     *   [PathMatch.asArray] to work with the elements in code.
     *
     * ### Errors
     * - Throws [JsonPathDecodingException] if:
     *   - the response body is not valid JSON,
     *   - the JSONPath syntax is invalid, or
     *   - the path cannot be resolved (no match).
     *
     * ### Examples
     * Basic scalar:
     * ```kotlin
     * // Given: { "x": "y", "numbers": [1,2,3] }
     * result.jsonPath("$.x").asText()                  // -> "y"
     * result.jsonPath("$.numbers[0]").asInt()          // -> 1
     * result.jsonPath("$.numbers")[0].asInt()          // -> 1
     * ```
     *
     * Wildcards & slices:
     * ```kotlin
     * // Given: { "list": ["a", "b", "c"] }
     * val list = result.jsonPath("$.list[*]").asArray()
     * list.size()                 // -> 3
     * list[1].asText()            // -> "b"
     *
     * result.jsonPath("$.list[1:3]").asArray()[0].asText()  // -> "b"
     * result.jsonPath("$.list[1:3]")[0].asText()            // -> "b"
     * ```
     *
     * Regex filters (Java regex):
     * ```kotlin
     * // Given:
     * // { "items":[
     * //   {"id":1,"name":"Banana"},
     * //   {"id":2,"name":"apple"},
     * //   {"id":3,"name":"blueberry"},
     * //   {"id":4,"name":"Cherry"},
     * //   {"id":5,"name":"beet"} ] }
     * val matches = result
     *   .jsonPath("$.items[?(@.name =~ /(?i)^b.*$/)].name")
     *   .asArray()                // -> ["Banana","blueberry","beet"]
     * ```
     *
     * Aggregates (Jayway functions):
     * ```kotlin
     * // Given: { "numbers": [1, 2, 3, 4.5] }
     * result.jsonPath("$.numbers.max()").asDouble()  // -> 4.5
     * result.jsonPath("$.numbers.sum()").asDouble()  // -> 10.5
     * ```
     *
     * Cross-field comparison:
     * ```kotlin
     * // Given:
     * // { "meta": { "maxPrice": 25.0 }, "items": [
     * //   {"id":1,"name":"A","price":9.99},
     * //   {"id":2,"name":"B","price":25.0},
     * //   {"id":3,"name":"C","price":19.5},
     * //   {"id":4,"name":"D","price":30.0} ] }
     * val arr = result.jsonPath("$.items[?(@.price < $.meta.maxPrice)]").asArray()
     * arr.size()                  // -> 2
     * arr[0]["name"].asText()     // -> "A"
     * arr[0]["price"].asDouble()  // -> 9.99
     * arr.first()["name"].asText()     // -> "A"
     * arr.first()["price"].asDouble()  // -> 9.99
     * arr[1]["name"].asText()     // -> "C"
     * arr[1]["price"].asDouble()  // -> 19.5
     * ```
     *
     * @param expr JSONPath expression (Jayway syntax), e.g. `"$.nested[?(@.check==2)].value"`.
     * @return A [PathMatch] wrapping the matched node; use its strict accessors (e.g., `asText()`, `asInt()`)
     *         or `asArray()` for array results.
     * @throws JsonPathDecodingException if parsing or path evaluation fails.
     *
     * @since 0.1.0
     */
    fun jsonPath(expr: String): PathMatch {
        val jsonStr = responseText()

        val compiled = try {
            JsonPath.compile(expr)
        } catch (e: InvalidPathException) {
            throw JsonPathDecodingException("Failed to evaluate JSONPath '$expr'", e)
        } catch (e: Exception) {
            throw JsonPathDecodingException("Failed to evaluate JSONPath '$expr'", e)
        }

        var wasMissing = false
        val raw: Any? = try {
            JsonPath.parse(jsonStr).read<Any?>(compiled)
        } catch (_: PathNotFoundException) {
            wasMissing = true
            null
        } catch (e: Exception) {
            throw JsonPathDecodingException("Failed to evaluate JSONPath '$expr'", e)
        }

        val node: JacksonNode? = when {
            wasMissing -> null                                 // truly missing
            raw == null -> NullNode.getInstance()              // present but JSON null
            else -> JSON_MAPPER.valueToTree(raw)
        }
        return PathMatch(node, expr)
    }


}

private fun extractCharset(contentType: String): Charset? {
    return contentType
        .split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter("=")
        ?.let {
            try {
                Charset.forName(it)
            } catch (_: Exception) {
                null
            }
        }
}