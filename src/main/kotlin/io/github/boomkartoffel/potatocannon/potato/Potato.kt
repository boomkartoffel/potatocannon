package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.strategy.PotatoSetting
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.HeaderStrategy
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.LogCommentary
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8


/**
 * Represents the body of an HTTP request used by the Potato Cannon.
 *
 * Implementations model different payload forms (text vs. binary).
 *
 * @since 0.1.0
 */
sealed interface PotatoBody

/**
 * Plain-text request body; sent as-is.
 *
 * @property content Raw textual payload.
 * @property charset Charset used to encode [content] (defaults to UTF-8).
 * @property includeCharset Whether a `charset=...` parameter should be added to the `Content-Type` header
 *                          by whichever component builds headers (typically for `text/...` types).
 *                          Defaults to `false`.
 *                          If `true`, the charset is appended to the `Content-Type` header, e.g.: `Content-Type: text/plain; charset=UTF-8`.
 *                          If `true` AND the `Content-Type` header is missing, the cannon will throw an error.
 * @since 0.1.0
 */
class TextBody @JvmOverloads constructor(
    val content: String,
    val charset: Charset = UTF_8,
    val includeCharset: Boolean = false
) : PotatoBody {

    /**
     * Constructor to set only the include-charset flag while keeping UTF-8.
     *
     * Example:
     * ```
     * val body = TextBody("hello", true); // UTF-8, and add ; charset=UTF-8 to Content-Type
     * ```
     */
    constructor(content: String, includeCharset: Boolean) : this(
        content = content,
        charset = UTF_8,
        includeCharset = includeCharset
    )
}

/**
 * Binary request body.
 *
 * @property content Raw bytes to send as the request payload.
 * @since 0.1.0
 */
class BinaryBody(val content: ByteArray) : PotatoBody

/**
 * HTTP request methods (RFC 9110).
 *
 * @since 0.1.0
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    TRACE
}

/**
 * Represents an HTTP request definition to be fired by the [Cannon].
 *
 * A `Potato` encapsulates everything needed to define an HTTP call, including method, path,
 * optional body, and optional settings such as headers, query parameters, and expectations.
 *
 * This class is immutable; use `with*` methods to modify and derive new instances.
 *
 * @property method The HTTP method to use (e.g. GET, POST, PUT, DELETE).
 * @property path The relative path of the request (e.g. "/users").
 * @property body Optional request body, either textual or binary.
 * @property settings Optional list of [PotatoSetting] items like [HeaderStrategy], [QueryParam], [LogCommentary] or [Expectation] of the request.
 * @since 0.1.0
 */
class Potato(
    val method: HttpMethod,
    val path: String,
    val body: PotatoBody?,
    val settings: List<PotatoSetting> = listOf()
) {

    constructor(
        method: HttpMethod,
        path: String,
        vararg settings: PotatoSetting
    ) : this(method, path, null, settings.toList())

    constructor(
        method: HttpMethod,
        path: String,
        settings: List<PotatoSetting>
    ) : this(method, path, null, settings)

    constructor(
        method: HttpMethod,
        path: String,
        body: PotatoBody,
        vararg settings: PotatoSetting
    ) : this(method, path, body, settings.toList())

    /**
     * Returns a copy of this [Potato] with a different HTTP method.
     *
     * Keeps the current path, body, and settings unchanged.
     *
     * @param newMethod The HTTP method to set (e.g., GET, POST).
     * @return A new [Potato] with [newMethod].
     * @since 0.1.0
     */
    fun withMethod(newMethod: HttpMethod): Potato =
        Potato(newMethod, path, body, settings)

    /**
     * Returns a copy of this [Potato] with a different request path.
     *
     * Keeps the current method, body, and settings unchanged.
     *
     * @param newPath The request path (e.g., "/health").
     * @return A new [Potato] with [newPath].
     * @since 0.1.0
     */
    fun withPath(newPath: String): Potato =
        Potato(method, newPath, body, settings)

    /**
     * Adds an [Expectation] to this [Potato]’s settings.
     *
     * This is a convenience for [addSettings]; the expectation is appended to the
     * end of the settings list, preserving existing entries.
     *
     * @param expectation The expectation to add.
     * @return A new [Potato] with the expectation appended.
     * @since 0.1.0
     */
    fun addExpectation(expectation: Expectation): Potato =
        this.addSettings(expectation)

    /**
     * Returns a copy of this [Potato] with a different request body.
     *
     * Passing `null` clears the body. Method, path, and settings remain unchanged.
     *
     * @param newBody The new body to use, or `null` to remove it.
     * @return A new [Potato] with [newBody].
     * @since 0.1.0
     */
    fun withBody(newBody: PotatoBody?): Potato =
        Potato(method, path, newBody, settings)

    /**
     * Replaces the entire settings list for this [Potato].
     *
     * Use this to set an explicit settings snapshot. If you want to **append**
     * additional options to the current list instead, prefer [addSettings].
     *
     * @param newSettings The complete [PotatoSetting] list to use.
     * @return A new [Potato] with [newSettings].
     * @since 0.1.0
     */
    fun withSettings(newSettings: List<PotatoSetting>): Potato =
        Potato(method, path, body, newSettings)

    /**
     * Vararg convenience for replacing the entire settings list.
     *
     * If you want to **append** items to the existing settings, use
     * [addSettings] instead.
     *
     * @param newSettings The complete [PotatoSetting] to set.
     * @return A new [Potato] with [newSettings].
     * @since 0.1.0
     */
    fun withSettings(vararg newSettings: PotatoSetting): Potato = this.withSettings(newSettings.toList())

    /**
     * Appends one or more settings entries to this [Potato].
     *
     * Existing settings entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedSettings The [PotatoSetting] entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addSettings(vararg addedSettings: PotatoSetting): Potato =
        this.withSettings(settings + addedSettings)

    /**
     * Appends one or more settings entries to this [Potato].
     *
     * Existing settings entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedSettings The [PotatoSetting] entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addSettings(addedSettings: List<PotatoSetting>): Potato =
        this.withSettings(settings + addedSettings)

}