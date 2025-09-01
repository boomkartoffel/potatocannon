package io.github.boomkartoffel.potatocannon.result

import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import java.nio.charset.Charset

private val defaultCharset = Charsets.UTF_8
private val defaultFormat = DeserializationFormat.JSON
private const val contentTypeHeaderName = "content-type"

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
 * Contains full details about the request, response, and any exception that occurred.
 *
 * @property potato The original [Potato] that was fired.
 * @property fullUrl The full URL used in the request, including query parameters.
 * @property statusCode The HTTP response status code (e.g., 200, 404).
 * @property responseBody The raw response body as a [ByteArray], or null if the request failed or had no body.
 * @property requestHeaders The headers sent with the request.
 * @property responseHeaders The headers received in the response.
 * @property queryParams The query parameters used in the request.
 * @property durationMillis Total time in milliseconds taken to execute the request.
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
//    val error: Throwable?,
    val attempts: Int
) {

    fun responseText(charset: Charset) = responseBody?.toString(charset)
    fun responseText() = responseText(responseCharset())

    fun <T> bodyAsObject(clazz: Class<T>): T = bodyAsObject(clazz, defaultFormat)
    fun <T> bodyAsObject(clazz: Class<T>, charset: Charset): T = bodyAsObject(clazz, defaultFormat, charset)
    fun <T> bodyAsObject(clazz: Class<T>, format: DeserializationFormat): T =
        bodyAsObject(clazz, format, responseCharset())

    fun <T> bodyAsObject(clazz: Class<T>, deserializer: Deserializer): T =
        bodyAsObject(clazz, deserializer, responseCharset())

    fun <T> bodyAsObject(clazz: Class<T>, format: DeserializationFormat, charset: Charset): T {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer(deserializationStrategies)
            DeserializationFormat.XML -> XmlDeserializer(deserializationStrategies)
        }

        return bodyAsObject(clazz, deserializer, charset)
    }

    fun <T> bodyAsList(clazz: Class<T>): List<T> = bodyAsList(clazz, defaultFormat, responseCharset())
    fun <T> bodyAsList(clazz: Class<T>, charset: Charset): List<T> =
        bodyAsList(clazz, defaultFormat, charset)

    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat): List<T> =
        bodyAsList(clazz, format, responseCharset())

    fun <T> bodyAsList(clazz: Class<T>, deserializer: Deserializer): List<T> =
        bodyAsList(clazz, deserializer, responseCharset())

    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat, charset: Charset): List<T> {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer(deserializationStrategies)
            DeserializationFormat.XML -> XmlDeserializer(deserializationStrategies)
        }

        return bodyAsList(clazz, deserializer, charset)
    }


    fun <T> bodyAsObject(clazz: Class<T>, deserializer: Deserializer, charset: Charset): T {
        val text = responseText(charset)
            ?: throw ResponseBodyMissingException()
        return try {
            deserializer.deserializeObject(text, clazz)
        } catch (e: Exception) {
            throw DeserializationFailureException(clazz.name, e)
        }
    }

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