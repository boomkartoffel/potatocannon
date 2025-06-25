package io.github.boomkartoffel.potatocannon.strategy

import java.util.Base64

/**
 * Strategy interface for applying headers to HTTP requests.
 *
 * Implementing classes or enums define how headers are added or modified
 * before a Potato (request) is fired. These strategies can be applied
 * at the Potato or Cannon level.
 */
sealed interface HeaderStrategy : CannonConfiguration, PotatoConfiguration {
    fun apply(headers: MutableMap<String, List<String>>)
}

/**
 * Predefined `Content-Type` headers for common MIME types.
 *
 * When applied, sets the `Content-Type` header to the associated MIME type of the request body.
 * There can be only one `Content-Type` header per request, so this strategy will overwrite any existing `Content-Type` header.
 */
enum class ContentHeader(val mime: String) : HeaderStrategy {
    JSON("application/json"),
    XML("application/xml"),
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    TEXT_PLAIN("text/plain"),
    MULTIPART_FORM_DATA("multipart/form-data"),
    JAVASCRIPT("application/javascript"),
    OCTET_STREAM("application/octet-stream"),
    PDF("application/pdf"),
    ZIP("application/zip"),
    CSV("text/csv"),
    HTML("text/html"),
    RTF("application/rtf"),
    EVENT_STREAM("text/event-stream"),
    NDJSON("application/x-ndjson"),
    YAML("application/x-yaml");

    override fun apply(headers: MutableMap<String, List<String>>) {
        headers["Content-Type"] = listOf(mime)
    }
}

/**
 * Basic authentication header strategy.
 *
 * Applies a Basic Auth header using the provided username and password.
 * There can be only one `Authorization` header per request, so this strategy will overwrite any existing `Authorization` header.
 *
 * @property username The username for Basic Auth.
 * @property password The password for Basic Auth.
 */
class BasicAuth(val username: String, val password: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, List<String>>) {
        val encoded = "$username:$password"
            .encodeToByteArray()
            .let { Base64.getEncoder().encodeToString(it) }

        headers["Authorization"] = listOf("Basic $encoded")
    }
}


/**
 * Bearer authentication header strategy.
 *
 * Applies a Bearer token header for OAuth 2.0 or similar token-based authentication.
 * There can be only one `Authorization` header per request, so this strategy will overwrite any existing `Authorization` header.
 *
 * @property token The Bearer token to set in the `Authorization` header.
 */
class BearerAuth(val token: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, List<String>>) {
        headers["Authorization"] = listOf("Bearer $token")
    }
}

/**
 * Enum representing strategies for updating headers when a header with the same key already exists.
 *
 * - `APPEND`: Adds the new value to the existing values for the header key.
 * - `OVERWRITE`: Replaces the existing values with the new value for the header key.
 */
enum class HeaderUpdateStrategy {
    APPEND,
    OVERWRITE;
}

/**
 * Custom header strategy for adding or updating headers.
 *
 * Allows specifying any key-value pair as a header, with a configurable strategy that determines how to handle cases
 * where the same header key already exists.
 *
 * For example:
 * ```
 * CustomHeader("Append-Header", "AppendValue"),
 * CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.APPEND)
 * ```
 * In this case, the second header will be appended rather than overwritten due to the provided strategy.
 *
 * @property key The header key to set.
 * @property value The header value to set.
 * @property strategy The strategy to use when the header already exists. Defaults to [HeaderUpdateStrategy.OVERWRITE].
 */
class CustomHeader(val key: String, val value: String, val strategy: HeaderUpdateStrategy) : HeaderStrategy {

    constructor(key: String, value: String) : this(key, value, HeaderUpdateStrategy.OVERWRITE)

    override fun apply(headers: MutableMap<String, List<String>>) {
        when (strategy) {
            HeaderUpdateStrategy.OVERWRITE -> {
                headers[key] = listOf(value)
            }

            HeaderUpdateStrategy.APPEND -> {
                val existing = headers[key]
                val updated = if (existing == null) {
                    listOf(value)
                } else {
                    existing + value
                }
                headers[key] = updated
            }
        }
    }
}

/**
 * Strategy for setting a `Cookie` header.
 *
 * Applies a `Cookie` header with the specified cookie string.
 * There can be only one `Cookie` header per request, so this strategy will overwrite any existing `Cookie` header.
 *
 * @property cookie The cookie string to set in the `Cookie` header.
 */
class CookieHeader(val cookie: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, List<String>>) {
        headers["Cookie"] = listOf(cookie)
    }
}