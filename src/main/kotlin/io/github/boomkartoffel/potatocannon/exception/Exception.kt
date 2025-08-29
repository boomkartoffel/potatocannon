package io.github.boomkartoffel.potatocannon.exception

sealed class PotatoCannonException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ResponseBodyMissingException
    : PotatoCannonException("Response body is null")

class DeserializationFailureException(
    className: String,
    cause: Throwable
) : PotatoCannonException("Failed to deserialize response body as $className\n${cause.message}", cause)

class ExecutionFailureException(
    cause: Throwable? = null
) : PotatoCannonException("Request execution failed", cause)