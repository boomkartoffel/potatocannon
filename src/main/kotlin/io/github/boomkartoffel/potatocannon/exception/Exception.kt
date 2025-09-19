package io.github.boomkartoffel.potatocannon.exception
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import java.util.concurrent.CancellationException
import java.lang.InterruptedException
import io.github.boomkartoffel.potatocannon.result.Result

sealed class PotatoCannonException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when a response expected to contain a body was empty or absent.
 **
 * Usually emitted by [Result.bodyAsObject] / [Result.bodyAsList] before parsing.
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
 * Thrown when a request fails during **execution** while waiting for the computation to complete.
 *
 *This exception is only created when awaiting the result fails due to:
 * - [CancellationException] — the computation was cancelled
 * - [InterruptedException] — the waiting thread was interrupted
 *
 * The original cause is preserved as [cause].
 *
 * @since 0.1.0
 */
class RequestExecutionException internal constructor(
    cause: Throwable
) : PotatoCannonException("Request execution failed", cause)



class VerificationException internal constructor(
    cause: Throwable
): PotatoCannonException("Verification of an expectation has failed", cause)

/**
 * Wraps failures that occur while **building** a request
 *
 * Typical sources:
 * - `URI.create(fullUrl)` throws for an invalid URL.
 * - `HttpRequest.Builder.header(...)` throws for illegal names/values.
 * - The charset of content-type is set but no mime type is defined.
 *
 * The original cause is preserved as [cause].
 *
 * @since 0.1.0
 */
class RequestPreparationException internal constructor(
    message: String,
    cause: Throwable?
) : PotatoCannonException(message, cause)

/**
 * Emitted when an attempt to **send** the request fails (I/O, timeouts, connection issues).
 *
 * This exception is thrown per attempt so that retry logic can decide whether to retry.
 * The [attempt] number is 1-based (first attempt is 1).
 *
 * @property attempt The 1-based attempt number that failed.
 * @since 0.1.0
 */
class RequestSendingFailureException internal constructor(
    cause: Throwable,
    attempt: Int,
    method: HttpMethod,
    url: String
) : PotatoCannonException("Failed to send ${method.name} request to $url within $attempt attempts: ${cause.message}", cause)