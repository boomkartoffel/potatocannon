package io.github.boomkartoffel.potatocannon.result

import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import java.nio.charset.Charset

private val defaultCharset = Charsets.UTF_8
private val defaultFormat = DeserializationFormat.JSON
private const val contentTypeHeaderName = "content-type"

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

    operator fun get(key: String): List<String>? {
        return normalized[key.lowercase()]
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
 * @property responseBody The raw response body as a [ByteArray], or null if the request had no body.
 * @property requestHeaders The [Headers] sent with the request.
 * @property responseHeaders The [Headers] received in the response.
 * @property queryParams The query parameters used in the request.
 * @property durationMillis Total time in milliseconds taken to execute the request.
 * @property attempts The number of attempts it took to successfully send the request.
 * @since 0.1.0
 */
class Result internal constructor(
    val potato: Potato,
    val fullUrl: String,
    val statusCode: Int,
    val responseBody: ByteArray?,
    val requestHeaders: Headers,
    val responseHeaders: Headers,
    val queryParams: Map<String, List<String>>,
    val durationMillis: Long,
    //this is not a configuration, but a list of strategies that are necessary for deserialization, and it is not supposed to be accessed by the user
    internal val deserializationStrategies: List<DeserializationStrategy>,
    val attempts: Int
) {

    /**
     * Decodes the raw response body into text using the given [charset].
     *
     * @param charset The character set to use when decoding the body.
     * @return The decoded response text, or `null` if the response has no body.
     * @since 0.1.0
     */
    fun responseText(charset: Charset) = responseBody?.toString(charset)

    /**
     * Decodes the raw response body into text by automatic detection.
     *
     * The charset is typically resolved from the `Content-Type` response header or defaults to UTF-8.
     *
     * @return The decoded response text, or `null` if the response has no body.
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
    fun <T> bodyAsObject(clazz: Class<T>, format: DeserializationFormat): T =
        bodyAsObject(clazz, format, responseCharset())

    /**
     * Deserializes the response body into one instance of [T] using the provided [deserializer]
     * and the detected response charset.
     *
     * @param deserializer A custom [Deserializer] implementation.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, deserializer: Deserializer): T =
        bodyAsObject(clazz, deserializer, responseCharset())

    /**
     * Deserializes the response body into one instance of [T] using the given [format]
     * and [charset]. The underlying deserializer is chosen based on the [DeserializationFormat] and will
     * apply any configured [DeserializationStrategy].
     *
     * @param format The wire format to use (e.g., JSON or XML).
     * @param charset Charset used to decode the response body prior to parsing.
     * @return A new instance of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsObject(clazz: Class<T>, format: DeserializationFormat, charset: Charset): T {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer(deserializationStrategies)
            DeserializationFormat.XML -> XmlDeserializer(deserializationStrategies)
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
     * @param format The [DeserializationFormat] to use (e.g., JSON or XML).
     * @return A [List] of [T].
     * @throws ResponseBodyMissingException if the response has no body.
     * @throws DeserializationFailureException if deserialization fails.
     * @since 0.1.0
     */
    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat): List<T> =
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
    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat, charset: Charset): List<T> {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer(deserializationStrategies)
            DeserializationFormat.XML -> XmlDeserializer(deserializationStrategies)
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
            ?: throw ResponseBodyMissingException()
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
            ?: throw ResponseBodyMissingException()
        return try {
            deserializer.deserializeList(text, clazz)
        } catch (e: Exception) {
            throw DeserializationFailureException(clazz.name, e)
        }
    }

    private fun responseCharset(): Charset {
        return responseHeaders[contentTypeHeaderName]
            ?.firstOrNull()
            ?.let { extractCharset(it) }
            ?: defaultCharset
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