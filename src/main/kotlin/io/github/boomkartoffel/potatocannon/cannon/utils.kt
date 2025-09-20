package io.github.boomkartoffel.potatocannon.cannon

import io.github.boomkartoffel.potatocannon.BuildConfig
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.strategy.CustomHeader
import io.github.boomkartoffel.potatocannon.strategy.HeaderUpdateStrategy
import io.github.boomkartoffel.potatocannon.strategy.PotatoCannonSetting
import java.net.URI

internal fun validUri(fullUrl: String): URI {
    val trimmed = fullUrl.trim()
    val uri = try {
        URI.create(trimmed)
    } catch (t: Throwable) {
        throw RequestPreparationException("Invalid URL syntax: $trimmed", t)
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw RequestPreparationException(
            "Unsupported or missing scheme in URL: $trimmed (only http/https are supported)", null
        )
    }

    if (uri.toURL().host.isNullOrEmpty()) {
        throw RequestPreparationException(
            "URL must be absolute and include a host (e.g., https://example.com/path): $trimmed", null
        )
    }

    return uri
}

private val allowedHeaderName = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

/** true if string contains control chars other than HTAB (0x09) */
private fun hasCtlExceptHTab(s: String): Boolean =
    s.any { ch ->
        val c = ch.code
        (c in 0..31 && c != 9) || c == 127
    }

internal fun validHeader(name: String, value: String): Pair<String, String> {
    val rawName = name.trim()
    if (rawName.isEmpty()) {
        throw RequestPreparationException("Header name is empty", null)
    }
    if (!allowedHeaderName.matches(rawName)) {
        throw RequestPreparationException(
            "Invalid header name: '$name' (must match RFC token, e.g. letters, digits, and -!#$%&'*+.^_`|~; no colon/space)",
            null
        )
    }

    // Reject CR/LF outright (no obs-folding allowed)
    if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
        throw RequestPreparationException(
            "Invalid value for header '$rawName': contains CR/LF (line breaks are not allowed)", null
        )
    }
    // Reject other control chars except HTAB
    if (hasCtlExceptHTab(value)) {
        throw RequestPreparationException(
            "Invalid value for header '$rawName': contains control characters", null
        )
    }

    // Trim outer OWS (space / tab) per spec; inner spaces are preserved
    val normalizedValue = value.trim { it == ' ' || it == '\t' }

    // Normalize name to lowercase for consistent internal maps (HTTP header names are case-insensitive)
    val normalizedName = rawName.lowercase()

    return normalizedName to normalizedValue
}

internal fun userAgentStrategy() =
    CustomHeader("user-agent", "PotatoCannon/${BuildConfig.VERSION}", HeaderUpdateStrategy.OVERWRITE)

internal inline fun <reified T : PotatoCannonSetting> List<PotatoCannonSetting>.lastSettingWithDefault(default: PotatoCannonSetting): T =
    asSequence().filterIsInstance<T>().lastOrNull() ?: (default as T)