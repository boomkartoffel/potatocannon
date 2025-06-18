package io.github.boomkartoffel.potatocannon.strategy

import java.util.Base64

sealed interface HeaderStrategy : CannonConfiguration, PotatoConfiguration {
    fun apply(headers: MutableMap<String, String>)
}

class BasicAuth(val username: String, val password: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, String>) {
        val encoded = "$username:$password"
            .encodeToByteArray()
            .let { Base64.getEncoder().encodeToString(it) }

        headers["Authorization"] = "Basic $encoded"
    }
}

class BearerAuth(val token: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, String>) {
        headers["Authorization"] = "Bearer $token"
    }
}

class CustomHeader(val key: String, val value: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, String>) {
        headers[key] = value
    }
}

class CookieHeader(val cookie: String) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, String>) {
        headers["Cookie"] = cookie
    }
}

enum class ContentType(val mime: String) {
    JSON("application/json"),
    XML("application/xml"),
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    TEXT_PLAIN("text/plain"),
    MULTIPART_FORM_DATA("multipart/form-data");
}

class ContentHeader(private val contentType: ContentType) : HeaderStrategy {
    override fun apply(headers: MutableMap<String, String>) {
        headers["Content-Type"] = contentType.mime
    }
}

class QueryParam(val key: String, val value: String) : PotatoConfiguration, CannonConfiguration {
    fun apply(queryParams: MutableMap<String, List<String>>) {
        if (!queryParams.containsKey(key)) {
            queryParams[key] = mutableListOf(value)
        } else {
            queryParams[key] = queryParams[key]!!.toMutableList().apply { add(value) }
        }
    }
}

enum class LoggingStrategy {
    NONE,
    BASIC,
    HEADERS,
    SAFE_HEADERS, // Like HEADERS but masks or skips sensitive headers
    BODY,
    FULL
}


class Logging(strategy: LoggingStrategy) : CannonConfiguration, PotatoConfiguration