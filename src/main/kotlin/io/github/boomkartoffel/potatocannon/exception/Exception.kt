package io.github.boomkartoffel.potatocannon.exception

sealed class PotatoCannonException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when a response expected to contain a body was empty or absent.
 **
 * Usually emitted by [io.github.boomkartoffel.potatocannon.result.Result.bodyAsObject] / [io.github.boomkartoffel.potatocannon.result.Result.bodyAsList] before parsing.
 *
 * @since 0.1.0
 */
class ResponseBodyMissingException internal constructor() : PotatoCannonException("Response body is null")

/**
 * Thrown when deserialization of the response body into the target type fails.
 *
 * Examples:
 * - Malformed JSON/XML
 * - Type mismatches (e.g., string where a number is expected)
 * - Enum value not recognized and no default configured
 *
 * The original parsing exception is available as [cause].
 *
 * @since 0.1.0
 */
class DeserializationFailureException internal constructor(
    className: String,
    cause: Throwable
) : PotatoCannonException("Failed to deserialize response body as $className\n${cause.message}", cause)

/**
 * Wraps failures that occur while **building** or **preparing** a request
 * (e.g., invalid URI, illegal header name/value) or while handling the
 * response post-send.
 *
 * Typical sources:
 * - `URI.create(fullUrl)` throws for an invalid URL.
 * - `HttpRequest.Builder.header(...)` throws for illegal names/values.
 *
 * The original cause is preserved as [cause].
 *
 * @since 0.1.0
 */
class ExecutionFailureException internal constructor(
    cause: Throwable
) : PotatoCannonException("Request execution failed", cause)

/**
 * Emitted when an attempt to **send** the request fails (I/O, timeouts, connection issues).
 *
 * This exception is thrown per attempt so that retry logic can decide whether to retry.
 * The [attempt] number is 1-based (first attempt is 1).
 *
 * @property attempt The 1-based attempt number that failed.
 * @since 0.1.0
 */
class RequestSendingException internal constructor(
    cause: Throwable,
    attempt: Int
) : PotatoCannonException("Failed to send request within $attempt attempts", cause)