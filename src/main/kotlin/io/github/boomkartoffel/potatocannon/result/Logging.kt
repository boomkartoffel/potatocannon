package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.github.boomkartoffel.potatocannon.potato.ConcretePotatoBody
import io.github.boomkartoffel.potatocannon.strategy.*
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_RED = "\u001B[31m"

private const val exclamationSign = "⚠️"

private object Json {
    val mapper: ObjectMapper = ObjectMapper()
    val xmlMapper : XmlMapper = XmlMapper()
}

internal fun Result.log(
    strategy: Logging,
    logExcludes: Set<LogExclude>,
    expectationResults: List<ExpectationResult>,
    logCommentary: List<LogCommentary>
) {

    if (strategy == Logging.OFF) return

    val builder = StringBuilder()

    val headerLine = "═".repeat(20) + "  🥔  Potato Dispatch Log 🥔  " + "═".repeat(20)

    builder.appendLine()
    builder.appendLine(headerLine)

    if (logCommentary.isNotEmpty()) {
        builder.appendLine("| ")
        logCommentary
            .forEach { builder.appendLine("| ℹ\uFE0F ${it.message}") }
        builder.appendLine("| ")
    }

    builder.appendLine("| ⏩ Request")
    builder.appendLine("|    Method:     ${potato.method}")
    builder.appendLine("|    Path:       ${potato.path}")

    if (logExcludes.none { it == LogExclude.FULL_URL }) {
        builder.appendLine("|    Full URL:   $fullUrl")
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

    builder.appendLine("|    Protocol:       ${protocol.token}")

    if (requestBody != null && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.BODY }) {
        builder.appendLine("|    Body:")
        builder.appendLine(requestBody.prettifyIndented())
    }

    if (attempts > 1) {
        builder.appendLine("|    $exclamationSign $attempts attempts at connecting $exclamationSign")
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


    if (!responseBody.isEmpty() && strategy >= Logging.FULL && logExcludes.none { it == LogExclude.BODY }) {
        builder.appendLine("|    Body:")

        builder.appendLine(responseText().prettifyIndented())
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

            val label = if (unnamed.size == 1) "1 unnamed check" else "${unnamed.size} unnamed checks"
            builder.appendLine("|      $checkmark $label ($passed $greenCheck | $assertionFailed $redCross | $otherFailed $exclamationSign)")

            val errorDescriptions = unnamed
                .filter { !it.isAssertionError && it.error != null }
                .map {
                    val err = it.error!!
                    "${err::class.simpleName}: ${err.message}".prettifyIndented()
                }

            val errorBlock = errorDescriptions.joinToString("\n|\n")
            if (errorBlock.isNotEmpty()) {
                builder.appendLine(errorBlock)
            }
        }

        // List named verifications with pass/fail marker
        named.forEachIndexed { idx, vr ->
            val desc = vr.expectation.description
            var needsErrorDescription = false

            val checkmark = if (vr.isAssertionError) {
                redCross
            } else if (vr.error != null) {
                exclamationSign.also { needsErrorDescription = true }
            } else {
                greenCheck
            }

            builder.appendLine("|      $checkmark $desc")

            if (needsErrorDescription) {
                val err = vr.error!!
                builder.appendLine("${err::class.simpleName}: ${err.message}".prettifyIndented())
            }

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


private fun ConcretePotatoBody?.prettifyIndented(): String = this?.getContentAsString().prettifyIndented()

private fun String?.prettifyIndented(): String {
    val prefix = "|      "
    val bodyContent = prettifyJsonIfPossible() ?: prettifyXmlIfPossible() ?:this?.trimIndent().orEmpty()
    return if (bodyContent.isEmpty()) "" else bodyContent.prependEachLine(prefix)
}

private fun String.prependEachLine(prefix: String): String =
    this.lines().joinToString("\n") { "$prefix$it" }


private fun String?.prettifyJsonIfPossible(): String? {
    // Treat null/blank as “not JSON”
    val s = this?.trim()
    if (s.isNullOrEmpty()) return null

    return try {
        val node = Json.mapper.readTree(s) ?: return null
        // Only pretty-print objects/arrays; ignore scalars (including JSON null)
        if (!node.isContainerNode) return null
        Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
    } catch (_: Exception) {
        null
    }
}

private fun String?.prettifyXmlIfPossible(): String? {

    var s = this?.trim()
    if (s.isNullOrEmpty()) return null

    // Strip UTF-8 BOM and any junk before first '<'
    if (s.startsWith('\uFEFF')) {
        s = s.removePrefix("\uFEFF")
    }
    val firstLt = s.indexOf('<')
    if (firstLt < 0) return null
    if (firstLt > 0) s = s.substring(firstLt)

    // Cheap precheck to avoid parsing random text
    if (!s.startsWith('<')) return null
    if (s.indexOf('>') < 0) return null

    return try {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            // Block loading external stuff (JDK 8uXX+; ignore if not supported)
            try {
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            } catch (_: Exception) {}
        }

        val builder = dbf.newDocumentBuilder().apply {
            setErrorHandler(object : ErrorHandler {
                override fun warning(e: SAXParseException?) = Unit
                override fun error(e: SAXParseException?) = Unit
                override fun fatalError(e: SAXParseException?) = Unit
            })
        }

        val doc = builder.parse(InputSource(StringReader(s)))

        fun stripWhitespaceTextNodes(node: Node) {
            var child = node.firstChild
            val toRemove = mutableListOf<Node>()
            while (child != null) {
                if (child.nodeType == Node.TEXT_NODE) {
                    if (child.nodeValue?.trim()?.isEmpty() == true) toRemove += child
                } else if (child.nodeType == Node.ELEMENT_NODE) {
                    stripWhitespaceTextNodes(child)
                }
                child = child.nextSibling
            }
            toRemove.forEach { node.removeChild(it) }
        }
        stripWhitespaceTextNodes(doc)

        val tf = TransformerFactory.newInstance()
        val transformer = tf.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") // no <?xml ...?>
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }

        val sw = StringWriter()
        transformer.transform(
            DOMSource(doc),
            StreamResult(sw)
        )
        sw.toString().trim()
    } catch (_: Exception) {
        null
    }

}
