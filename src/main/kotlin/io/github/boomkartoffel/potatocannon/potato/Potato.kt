package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.strategy.PotatoConfiguration
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.strategy.Expectation


sealed interface PotatoBody

class TextBody(val content: String) : PotatoBody
class BinaryBody(val content: ByteArray) : PotatoBody

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
 * optional body, and optional configurations such as headers, query parameters, and expectations.
 *
 * This class is immutable; use `with*` methods to modify and derive new instances.
 *
 * @property method The HTTP method to use (e.g. GET, POST, PUT, DELETE).
 * @property path The relative path of the request (e.g. "/users").
 * @property body Optional request body, either textual or binary.
 * @property configuration Optional list of [PotatoConfiguration] items like headers, query parameters, and verifications of the request.
 * @since 0.1.0
 */
class Potato(
    val method: HttpMethod,
    val path: String,
    val body: PotatoBody?,
    val configuration: List<PotatoConfiguration> = listOf()
) {

    constructor(
        method: HttpMethod,
        path: String,
        vararg configuration: PotatoConfiguration
    ) : this(method, path, null, configuration.toList())

    constructor(
        method: HttpMethod,
        path: String,
        configuration: List<PotatoConfiguration>
    ) : this(method, path, null, configuration)

    constructor(
        method: HttpMethod,
        path: String,
        body: PotatoBody,
        vararg configuration: PotatoConfiguration
    ) : this(method, path, body, configuration.toList())

    /**
     * Returns a copy of this [Potato] with a different HTTP method.
     *
     * Keeps the current path, body, and configuration unchanged.
     *
     * @param newMethod The HTTP method to set (e.g., GET, POST).
     * @return A new [Potato] with [newMethod].
     * @since 0.1.0
     */
    fun withMethod(newMethod: HttpMethod): Potato =
        Potato(newMethod, path, body, configuration)

    /**
     * Returns a copy of this [Potato] with a different request path.
     *
     * Keeps the current method, body, and configuration unchanged.
     *
     * @param newPath The request path (e.g., "/health").
     * @return A new [Potato] with [newPath].
     * @since 0.1.0
     */
    fun withPath(newPath: String): Potato =
        Potato(method, newPath, body, configuration)

    /**
     * Adds an [Expectation] to this [Potato]’s configuration.
     *
     * This is a convenience for [addConfiguration]; the expectation is appended to the
     * end of the configuration list, preserving existing entries.
     *
     * @param expectation The expectation to add.
     * @return A new [Potato] with the expectation appended.
     * @since 0.1.0
     */
    fun addExpectation(expectation: Expectation): Potato =
        this.addConfiguration(expectation)

    /**
    * Returns a copy of this [Potato] with a different request body.
    *
    * Passing `null` clears the body. Method, path, and configuration remain unchanged.
    *
    * @param newBody The new body to use, or `null` to remove it.
    * @return A new [Potato] with [newBody].
    * @since 0.1.0
    */
    fun withBody(newBody: PotatoBody?): Potato =
        Potato(method, path, newBody, configuration)

    /**
     * Replaces the entire configuration list for this [Potato].
     *
     * Use this to set an explicit configuration snapshot. If you want to **append**
     * additional options to the current list instead, prefer [addConfiguration].
     *
     * @param newConfiguration The complete configuration list to use.
     * @return A new [Potato] with [newConfiguration].
     * @since 0.1.0
     */
    fun withConfiguration(newConfiguration: List<PotatoConfiguration>): Potato =
        Potato(method, path, body, newConfiguration)

    /**
     * Vararg convenience for replacing the entire configuration list.
     *
     * Equivalent to `withConfiguration(newConfiguration.toList())`.
     * If you want to **append** items to the existing configuration, use
     * [addConfiguration] instead.
     *
     * @param newConfiguration The complete configuration to set.
     * @return A new [Potato] with [newConfiguration].
     * @since 0.1.0
     */
    fun withConfiguration(vararg newConfiguration: PotatoConfiguration): Potato = this.withConfiguration(newConfiguration.toList())

    /**
     * Appends one or more configuration entries to this [Potato].
     *
     * Existing configuration entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedConfiguration The configuration entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addConfiguration(vararg addedConfiguration: PotatoConfiguration): Potato =
        this.withConfiguration(configuration + addedConfiguration)

    /**
     * Appends one or more configuration entries to this [Potato].
     *
     * Existing configuration entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedConfiguration The configuration entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addConfiguration(addedConfiguration: List<PotatoConfiguration>): Potato =
        this.withConfiguration(configuration + addedConfiguration)

}