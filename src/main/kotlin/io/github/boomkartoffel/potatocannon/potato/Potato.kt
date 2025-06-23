package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.Result
import io.github.boomkartoffel.potatocannon.strategy.PotatoConfiguration


sealed interface PotatoBody

class TextBody(val content: String) : PotatoBody
class BinaryBody(val content: ByteArray) : PotatoBody


fun interface Expectation {
    fun verify(result: Result)
}

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
        body: PotatoBody,
        vararg configuration: PotatoConfiguration
    ) : this(method, path, body, configuration.toList())

    fun withMethod(newMethod: HttpMethod): Potato =
        Potato(newMethod, path, body, configuration)

    fun withPath(newPath: String): Potato =
        Potato(method, newPath, body, configuration)

    fun withBody(newBody: PotatoBody?): Potato =
        Potato(method, path, newBody, configuration)

    fun withConfiguration(vararg newConfiguration: PotatoConfiguration): Potato =
        Potato(method, path, body, newConfiguration.toList())

    fun withConfiguration(newConfiguration: List<PotatoConfiguration>): Potato =
        Potato(method, path, body, newConfiguration)
}