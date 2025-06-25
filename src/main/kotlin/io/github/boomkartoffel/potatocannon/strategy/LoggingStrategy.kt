package io.github.boomkartoffel.potatocannon.strategy

/**
 * Defines the verbosity level of logging.
 *
 * This configuration can be applied to either individual Potatoes or the entire Cannon.
 * When multiple logging configurations are applied, the last one takes precedence.
 *
 * The default logging level is [FULL], which logs all request and response details.
 */
enum class Logging : CannonConfiguration, PotatoConfiguration {
    /**
     * Disables all logging.
     */
    OFF,

    /**
     * Logs the basic request and response metadata:
     * - HTTP method and path
     * - Status code
     * - Execution time
     * - Any associated error
     */
    BASIC,

    /**
     * Enables full logging (optionally filtered by [LogExclude]):
     * - All request and response metadata from [BASIC]
     * - Headers
     * - Query parameters
     * - Request and response body
     * - All configured verifications
     */
    FULL;
}

/**
 * Excludes specific portions of the log output even when [Logging.FULL] is enabled.
 *
 * These exclusions can be combined and applied globally or per Potato.
 * Useful for reducing noise or hiding sensitive data in logs.
 *
 * There is no default exclusion, so all details are logged unless specified otherwise.
 */
enum class LogExclude : CannonConfiguration, PotatoConfiguration {
    /**
     * Excludes the full URL from the log.
     */
    FULL_URL,

    /**
     * Excludes all request and response headers.
     */
    HEADERS,

    /**
     * Excludes query parameters from the log.
     * They are still visible in the full URL.
     */
    QUERY_PARAMS,

    /**
     * Excludes request and response bodies.
     */
    BODY,

    /**
     * Masks sensitive headers in the log output.
     *
     * The following headers are considered:
     * - Authorization
     * - Proxy-Authorization
     * - X-Access-Token
     * - X-Api-Key
     * - X-Auth-Token
     * - X-Refresh-Token
     * - X-Csrf-Token
     * - Token
     * - Id-Token
     * - X-Id-Token
     * - Authorization-Bearer
     * - Cookie
     * - Set-Cookie
     * - Set-Cookie2
     */
    SECURITY_HEADERS,

    /**
     * Excludes the list of verification checks that were run on the response.
     */
    VERIFICATIONS;
}