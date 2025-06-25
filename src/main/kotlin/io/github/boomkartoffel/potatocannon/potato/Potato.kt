package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.strategy.PotatoConfiguration
import io.github.boomkartoffel.potatocannon.cannon.Cannon


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

    fun withMethod(newMethod: HttpMethod): Potato =
        Potato(newMethod, path, body, configuration)

    fun withPath(newPath: String): Potato =
        Potato(method, newPath, body, configuration)

    fun withBody(newBody: PotatoBody?): Potato =
        Potato(method, path, newBody, configuration)

    fun withConfiguration(newConfiguration: List<PotatoConfiguration>): Potato =
        Potato(method, path, body, newConfiguration)

    fun withConfiguration(vararg newConfiguration: PotatoConfiguration): Potato = this.withConfiguration(newConfiguration.toList())

    fun withAmendedConfiguration(vararg amendedConfiguration: PotatoConfiguration): Potato =
        this.withConfiguration(configuration + amendedConfiguration)

    fun withAmendedConfiguration(amendedConfiguration: List<PotatoConfiguration>): Potato =
        this.withConfiguration(configuration + amendedConfiguration)

}