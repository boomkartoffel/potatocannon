package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.PotatoBody
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.ExpectationResult
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.collections.component1
import kotlin.collections.component2

private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_RED = "\u001B[31m"

private const val exclamationSign = "⚠️"

internal fun Result.log(strategy: Logging, logExcludes: Set<LogExclude>, expectationResults: List<ExpectationResult>) {

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

    if (requestHeaders.toMap()
            .isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.HEADERS }
    ) {
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


    if (responseHeaders.toMap()
            .isNotEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.HEADERS }
    ) {
        builder.appendLine("|    Headers:")
        val mask = logExcludes.any { it == LogExclude.SECURITY_HEADERS }
        builder.appendLine(responseHeaders.toMap().logFilteredHeaders(mask).joinToString("\n"))
    }


    if (responseBody != null && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.BODY }) {
        builder.appendLine("|    Body:")

        builder.appendLine(responseText()?.prettifyIndented())
    }

    if (error != null) {
        builder.appendLine("| ")
        builder.appendLine("| $exclamationSign Error:")
        builder.appendLine("|    ${error::class.simpleName}: ${error.message}")

        val stackTrace = StringWriter().also { sw ->
            error.printStackTrace(PrintWriter(sw))
        }.toString()

        builder.appendLine(stackTrace.prettifyIndented())
    }

    val greenCheck = "$ANSI_GREEN✔$ANSI_RESET"
    val redCross = "$ANSI_RED✘$ANSI_RESET"

    if (expectationResults.isNotEmpty() &&
        strategy >= Logging.FULL &&
        logExcludes.none { it == LogExclude.VERIFICATIONS }
    ) {
        val (unnamed, named) = expectationResults.partition { it.expectation.description.isBlank() }

        builder.appendLine("| ")
        builder.appendLine("| 🔍 Expectations (${expectationResults.size}):")

        // Summarize unnamed verifications (no description)
        if (unnamed.isNotEmpty()) {
            val passed = unnamed.count { it.error == null }
            val assertionFailed = unnamed.count { it.isAssertionError }
            val otherFailed = unnamed.size - passed - assertionFailed

            val checkmark = if (assertionFailed > 0) {
                redCross
            } else if (otherFailed > 0) {
                exclamationSign
            } else {
                greenCheck
            }

            val label = if (unnamed.size == 1) "1 unnamed checks" else "${unnamed.size} unnamed check"
            builder.appendLine("|      $checkmark $label ($passed passed, $assertionFailed failed, $otherFailed other errors)")
        }

        // List named verifications with pass/fail marker
        named.forEachIndexed { idx, vr ->
            val desc = vr.expectation.description

            val checkmark = if (vr.isAssertionError) {
                redCross
            } else if (vr.error != null) {
                exclamationSign
            } else {
                greenCheck
            }

            builder.appendLine("|      $checkmark $desc")

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

