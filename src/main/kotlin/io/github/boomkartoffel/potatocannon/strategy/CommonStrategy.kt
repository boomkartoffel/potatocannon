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

enum class ContentHeader(val mime: String) : HeaderStrategy {
    JSON("application/json"),
    XML("application/xml"),
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    TEXT_PLAIN("text/plain"),
    MULTIPART_FORM_DATA("multipart/form-data");

    override fun apply(headers: MutableMap<String, String>) {
        headers["Content-Type"] = mime
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

sealed interface LoggingConfiguration: CannonConfiguration, PotatoConfiguration

enum class Logging : LoggingConfiguration {
    OFF,
    BASIC,
    FULL;
}

enum class LogExclude: LoggingConfiguration {
    HEADERS,
    QUERY_PARAMS,
    BODY,
    SECURITY_HEADERS; // Sensitive headers like Authorization, Cookie, etc.
}