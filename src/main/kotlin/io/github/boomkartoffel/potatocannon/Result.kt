package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.PotatoBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.LoggingStrategy
import java.nio.charset.Charset

class Result(
    val potato: Potato,
    val fullUrl: String,
    val statusCode: Int,
    val responseBody: ByteArray?,
    val responseHeaders: Map<String, List<String>>,
    val requestHeaders: Map<String, List<String>>,
    val queryParams: Map<String, List<String>>,
    val durationMillis: Long,
    val error: Throwable?
) {

    fun responseText(charset: Charset): String? {
        return try {
            responseBody?.toString(charset)
        } catch (e: Exception) {
            null // invalid charset or malformed content
        }
    }

    fun responseText(): String? {
        return responseText(Charsets.UTF_8)
    }

    internal fun log(strategy: LoggingStrategy) {

        if (strategy == LoggingStrategy.NONE) return

        val builder = StringBuilder()

        builder.appendLine()
        builder.appendLine("                         \uD83E\uDD54  Potato Result \uD83E\uDD54 ")
        builder.appendLine("═".repeat(70))
        builder.appendLine("→ Request")
        builder.appendLine("   Method:  ${potato.method}")
        builder.appendLine("   Path:    ${potato.path}")
        builder.appendLine("   Full URL: $fullUrl")

        if (queryParams.isNotEmpty() && strategy >= LoggingStrategy.BASIC) {
            builder.appendLine("   Query Params:")
            queryParams.forEach { (key, values) ->
                builder.appendLine("     $key: ${values.joinToString(", ")}")
            }
        }


        if (requestHeaders.isNotEmpty() && strategy >= LoggingStrategy.HEADERS) {
            builder.appendLine("   Headers:")
            val mask = strategy <= LoggingStrategy.SAFE_HEADERS
            builder.appendLine(requestHeaders.logFilteredHeaders(mask).joinToString("\n"))
        }

        if (potato.body != null && strategy >= LoggingStrategy.BODY) {
            builder.appendLine("   Body:")
            builder.appendLine(potato.body.prettifyIndented())
        }

        builder.appendLine()
        builder.appendLine("← Response")
        builder.appendLine("   Status:  $statusCode")
        builder.appendLine("   Time:    ${durationMillis}ms")


        if (responseHeaders.isNotEmpty() && strategy >= LoggingStrategy.SAFE_HEADERS) {
            builder.appendLine("   Headers:")
            val mask = strategy == LoggingStrategy.SAFE_HEADERS
            builder.appendLine(responseHeaders.logFilteredHeaders(mask).joinToString("\n"))
        }

        if (responseBody != null && strategy >= LoggingStrategy.BODY) {
            builder.appendLine("   Body:")
            builder.appendLine(this.responseText()?.prettifyIndented())
        }

        if (error != null && strategy >= LoggingStrategy.BASIC) {
            builder.appendLine()
            builder.appendLine("⚠️  Error:")
            builder.appendLine("   ${error::class.simpleName}: ${error.message}")
        }

        builder.appendLine("═".repeat(70))
        println(builder.toString())
    }
}

private fun Map<String, List<String>>.logFilteredHeaders(mask: Boolean): List<String> {
    return this.entries.mapNotNull { (key, values) ->
        val lowerKey = key.lowercase()
        if (lowerKey in sensitiveHeaderNames) {
            if (mask) "     $key: *****"
            else null
        } else {
            "     $key: ${values.joinToString(", ")}"
        }
    }
}

private val sensitiveHeaderNames = setOf(
    "authorization", "proxy-authorization",
    "x-access-token", "x-api-key", "x-auth-token",
    "cookie", "set-cookie"
)


fun PotatoBody.prettifyIndented(): String {
    return when (this) {
        is BinaryBody -> this.content.joinToString(separator = "") { it.toString(16).padStart(2, '0') }
        is TextBody -> this.content.prettifyIndented()
    }
}

fun String.prettifyIndented(): String {
    return this.prettifyJsonIfPossible() ?: this.trimIndent()
        .lines()
        .joinToString("\n") { "     $it" }

}

fun String.prettifyJsonIfPossible(): String? {
    return try {
        val json = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(this)
        "     " + com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(json)
    } catch (e: Exception) {
        null // not JSON or parsing failed
    }
}