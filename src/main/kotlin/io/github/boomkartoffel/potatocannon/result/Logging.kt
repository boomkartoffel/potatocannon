package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.PotatoBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
import kotlin.collections.component1
import kotlin.collections.component2

internal fun Result.log(strategy: Logging, logExcludes: Set<LogExclude>, verification: List<ResultVerification>) {

    if (strategy == Logging.OFF) return

    val builder = StringBuilder()

    val headerLine = "═".repeat(20) + "  🥔  Potato Dispatch Log 🥔  " + "═".repeat(20)

    builder.appendLine()
    builder.appendLine(headerLine)
    builder.appendLine("| ⏩ Request")
    builder.appendLine("|    Method:  ${potato.method}")
    builder.appendLine("|    Path:    ${potato.path}")

    if (logExcludes.none { it == LogExclude.FULL_URL }) {
        builder.appendLine("|    Full URL: $fullUrl")
    }

    if (queryParams.isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.QUERY_PARAMS }) {
        builder.appendLine("|    Query Params:")
        queryParams.forEach { (key, values) ->
            builder.appendLine("|      $key: ${values.joinToString(", ")}")
        }
    }

    if (requestHeaders.toMap().isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.HEADERS }) {
        builder.appendLine("|    Headers:")
        val mask = logExcludes.any { it == LogExclude.SECURITY_HEADERS }
        builder.appendLine(requestHeaders.toMap().logFilteredHeaders(mask).joinToString("\n"))
    }

    if (potato.body != null && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.BODY }) {
        builder.appendLine("|    Body:")
        builder.appendLine(potato.body.prettifyIndented())
    }

    builder.appendLine("| ")
    builder.appendLine("| ⏪ Response")
    builder.appendLine("|    Status:  $statusCode")
    builder.appendLine("|    Time:    ${durationMillis}ms")


    if (responseHeaders.toMap().isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.HEADERS }) {
        builder.appendLine("|    Headers:")
        val mask = logExcludes.any { it == LogExclude.SECURITY_HEADERS }
        builder.appendLine(responseHeaders.toMap().logFilteredHeaders(mask).joinToString("\n"))
    }


    if (responseBody != null && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.BODY }) {
        builder.appendLine("|    Body:")

        val charset = responseHeaders["content-type"]
            ?.firstOrNull()
            ?.let(::extractCharset)
            ?: Charsets.UTF_8

        builder.appendLine(responseText(charset)?.prettifyIndented())
    }

    if (error != null) {
        builder.appendLine("| ")
        builder.appendLine("| ⚠️ Error:")
        builder.appendLine("|    ${error::class.simpleName}: ${error.message}")

        val stackTrace = StringWriter().also { sw ->
            error.printStackTrace(PrintWriter(sw))
        }.toString()

        builder.appendLine(stackTrace.prettifyIndented())
    }

    if (verification.isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.VERIFICATIONS }) {
        val (unnamed, named) = verification.partition { it.description.isBlank() }

        builder.appendLine("| ")
        builder.appendLine("| \uD83D\uDD0D️ Verifications (${verification.size}):")

        if (unnamed.isNotEmpty()) {
            val label =
                if (unnamed.size == 1) "1 undescribed verification" else "${unnamed.size} undescribed verifications"
            builder.appendLine("|      - $label")
        }

        named.forEach {
            builder.appendLine("|      - ${it.description}")
        }
    }

    println(builder.toString())
}


private fun Map<String, List<String>>.logFilteredHeaders(mask: Boolean): List<String> {
    return this.entries.mapNotNull { (key, values) ->
        val lowerKey = key.lowercase()
        val valuePrint = if (lowerKey in sensitiveHeaderNames && mask) {
            "*****"
        } else {
            values.joinToString(", ")
        }

        return@mapNotNull if (values.isEmpty()) {
            null // Skip empty headers
        } else
            "|      $key: $valuePrint"
    }
}

private val sensitiveHeaderNames = setOf(
    "authorization", "proxy-authorization",
    "x-access-token", "x-api-key", "x-auth-token",
    "x-refresh-token", "x-csrf-token",
    "token", "id-token", "x-id-token",
    "authorization-bearer",
    "cookie", "set-cookie", "set-cookie2"
)


private fun PotatoBody.prettifyIndented(): String {
    return when (this) {
        is BinaryBody -> "|      " + this.content.joinToString(separator = "") { it.toString(16).padStart(2, '0') }
        is TextBody -> this.content.prettifyIndented()
    }
}

private fun String.prettifyIndented(): String {
    val prefix = "|      "

    val prettyJson = prettifyJsonIfPossible()
    if (prettyJson != null) return prettyJson.prependEachLine(prefix)

    return this.trimIndent().prependEachLine(prefix)
}

private fun String.prependEachLine(prefix: String): String =
    this.lines().joinToString("\n") { "$prefix$it" }


private fun String.prettifyJsonIfPossible(): String? {
    return try {
        val jsonNode = ObjectMapper().readTree(this)
        ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(jsonNode)
    } catch (e: Exception) {
        null
    }
}

private fun extractCharset(contentType: String): Charset? {
    return contentType
        .split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter("=")
        ?.let {
            try {
                Charset.forName(it)
            } catch (e: Exception) {
                null
            }
        }
}