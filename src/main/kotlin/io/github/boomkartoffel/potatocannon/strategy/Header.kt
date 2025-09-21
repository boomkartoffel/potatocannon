package io.github.boomkartoffel.potatocannon.strategy

import java.util.Base64

/**
 * Strategy interface for applying headers to HTTP requests.
 *
 * Implementing classes or enums define how headers are added or modified
 * before a Potato (request) is fired. These strategies can be applied
 * at the Potato or Cannon level.
 *
 * @since 0.1.0
 */
sealed interface HeaderStrategy : CannonSetting, PotatoSetting {
    fun apply(headers: MutableMap<String, List<String>>)
}

/**
 * Predefined `Content-Type` headers for common MIME types.
 *
 * When applied, sets the `Content-Type` header to the associated MIME type of the request body.
 * There can be only one `Content-Type` header per request, so this strategy will overwrite any existing `Content-Type` header.
 *
 * @since 0.1.0
 */
enum class ContentType(val mime: String) : HeaderStrategy {
    // Application
    /** `application/json` */
    JSON("application/json"),
    /** `application/xml` */
    XML("application/xml"),
    /** `application/x-www-form-urlencoded` */
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    /** `application/javascript` */
    JAVASCRIPT("application/javascript"),
    /** `application/octet-stream` */
    OCTET_STREAM("application/octet-stream"),
    /** `application/rtf` */
    RTF("application/rtf"),
    /** `application/pdf` */
    PDF("application/pdf"),
    /** `application/zip` */
    ZIP("application/zip"),
    /** `application/gzip` */
    GZIP("application/gzip"),
    /** `application/x-tar` */
    TAR("application/x-tar"),
    /** `application/x-7z-compressed` */
    SEVEN_ZIP("application/x-7z-compressed"),
    /** `application/x-bzip2` */
    BZIP2("application/x-bzip2"),
    /** `application/x-ndjson` */
    NDJSON("application/x-ndjson"),
    /** `application/yaml` */
    YAML("application/yaml"),
    /** `application/ld+json` */
    JSON_LD("application/ld+json"),
    /** `application/epub+zip` */
    EPUB("application/epub+zip"),
    /** `application/xhtml+xml` */
    XHTML("application/xhtml+xml"),
    /** `application/manifest+json` */
    MANIFEST("application/manifest+json"),
    /** `application/problem+json` */
    PROBLEM_JSON("application/problem+json"),

    // Text
    /** `text/plain` */
    TEXT_PLAIN("text/plain"),
    /** `text/event-stream` */
    EVENT_STREAM("text/event-stream"),
    /** `text/csv` */
    CSV("text/csv"),
    /** `text/html` */
    HTML("text/html"),
    /** `text/css` */
    CSS("text/css"),
    /** `text/markdown` */
    MARKDOWN("text/markdown"),

    // Data
    /** `multipart/form-data` */
    MULTIPART_FORM_DATA("multipart/form-data"),

    // Images
    /** `image/png` */
    PNG("image/png"),
    /** `image/jpeg` */
    JPEG("image/jpeg"),
    /** Non-standard alias for `image/jpeg`. Prefer [JPEG]. */
    JPG("image/jpg"), // alias (non-standard but seen)
    /** `image/gif` */
    GIF("image/gif"),
    /** `image/svg+xml` */
    SVG("image/svg+xml"),
    /** `image/avif` */
    AVIF("image/avif"),
    /** `image/bmp` */
    BMP("image/bmp"),
    /** `image/tiff` */
    TIFF("image/tiff"),
    /** `image/vnd.microsoft.icon` */
    ICON("image/vnd.microsoft.icon"),
    /** image/x-icon */
    ICON_X("image/x-icon"), // alias used by some servers

    // Audio
    /** `audio/mpeg` */
    MP3("audio/mpeg"),
    /** `audio/wav` */
    WAV("audio/wav"),
    /** `audio/x-wav` -> non-standard alias for `audio/wav`. Prefer [WAV]. */
    WAV_X("audio/x-wav"), // alias used by some servers

    // Video
    /** `video/webm` */
    WEBM_VIDEO("video/webm"),
    /** `video/mp4` */
    MP4_VIDEO("video/mp4"),

    // Font
    /** `font/woff` */
    WOFF("font/woff"),
    /** `font/woff2` */
    WOFF2("font/woff2"),
    /** `font/ttf` */
    TTF("font/ttf"),
    /** `font/otf` */
    OTF("font/otf"),

    // Aliases / legacy (optional)
    /** `text/javascript` */
    TEXT_JAVASCRIPT("text/javascript"), // deprecated alias
    /** `application/x-yaml` */
    YAML_X("application/x-yaml"); // non-standard alias, still seen

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
 *
 * @since 0.1.0
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
 *
 * @since 0.1.0
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
 *
 * @since 0.1.0
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
 * @since 0.1.0
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
 * @since 0.1.0
 */
class CookieHeader(val cookie: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, List<String>>) {
        headers["Cookie"] = listOf(cookie)
    }
}