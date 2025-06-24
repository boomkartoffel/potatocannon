package io.github.boomkartoffel.potatocannon.result

import io.github.boomkartoffel.potatocannon.potato.Potato
import java.nio.charset.Charset

private val defaultCharset = Charsets.UTF_8
private val defaultSerialization = DeserializationFormat.JSON

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
 * @property error Any exception thrown during the request, or null if successful.
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
    val error: Throwable?
) {

    fun responseText(charset: Charset): String? {
        return try {
            responseBody?.toString(charset)
        } catch (e: Exception) {
            null // invalid charset or malformed content
        }
    }

    fun responseText(): String? {
        return responseText(defaultCharset)
    }

    fun <T> bodyAsSingle(clazz: Class<T>): T = bodyAsSingle(clazz, defaultSerialization)
    fun <T> bodyAsSingle(clazz: Class<T>, charset: Charset): T = bodyAsSingle(clazz, defaultSerialization, charset)
    fun <T> bodyAsSingle(clazz: Class<T>, format: DeserializationFormat): T = bodyAsSingle(clazz, format, defaultCharset)
    fun <T> bodyAsSingle(clazz: Class<T>, deserializer: SingleDeserializer): T =
        bodyAsSingle(clazz, deserializer, defaultCharset)

    fun <T> bodyAsSingle(clazz: Class<T>, format: DeserializationFormat, charset: Charset): T {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer
            DeserializationFormat.XML -> XmlDeserializer
        }

        return bodyAsSingle(clazz, deserializer, charset)
    }

    fun <T> bodyAsList(clazz: Class<T>): List<T> = bodyAsList(clazz, defaultSerialization, defaultCharset)
    fun <T> bodyAsList(clazz: Class<T>, charset: Charset): List<T> =
        bodyAsList(clazz, defaultSerialization, charset)

    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat): List<T> =
        bodyAsList(clazz, format, defaultCharset)

    fun <T> bodyAsList(clazz: Class<T>, deserializer: ListDeserializer): List<T> =
        bodyAsList(clazz, deserializer, defaultCharset)

    fun <T> bodyAsList(clazz: Class<T>, format: DeserializationFormat, charset: Charset): List<T> {
        val deserializer = when (format) {
            DeserializationFormat.JSON -> JsonDeserializer
            DeserializationFormat.XML -> XmlDeserializer
        }

        return bodyAsList(clazz, deserializer, charset)
    }


    fun <T> bodyAsSingle(clazz: Class<T>, deserializer: SingleDeserializer, charset: Charset): T {
        val text = responseText(charset)
            ?: throw IllegalStateException("Response body is null")
        return try {
            deserializer.deserializeSingle(text, clazz)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize response body as ${clazz.name}", e)
        }
    }

    fun <T> bodyAsList(clazz: Class<T>, deserializer: ListDeserializer, charset: Charset): List<T> {
        val text = responseText(charset)
            ?: throw IllegalStateException("Response body is null")
        return try {
            deserializer.deserializeList(text, clazz)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to deserialize response body as List<${clazz.name}>", e)
        }
    }


}
