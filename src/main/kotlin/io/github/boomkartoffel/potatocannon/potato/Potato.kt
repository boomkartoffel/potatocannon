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
    val configuration: List<PotatoConfiguration> = listOf(),
) {
    constructor(
        method: HttpMethod,
        path: String
    ) : this(method, path, null, listOf())

    constructor(
        method: HttpMethod,
        path: String,
        body: PotatoBody
    ) : this(method, path, body, listOf())

    constructor(
        method: HttpMethod,
        path: String,
        configuration: List<PotatoConfiguration>
    ) : this(method, path, null, configuration)
}