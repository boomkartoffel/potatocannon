package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.result.Result

/**
 * A configuration that defines how a result should be verified after a request is fired.
 *
 * Combines an [Expectation] with an optional human-readable [description] for logging.
 *
 * This configuration can be applied to individual Potatoes or globally to the Cannon.
 *
 * @param expectation the condition that should be met by the [Result].
 * @param description an optional description of the verification. This text will be shown in the log when the verification is executed.
 */
class ResultVerification(val description: String, private val expectation: Expectation) : PotatoConfiguration,
    CannonConfiguration {

    constructor(expectation: Expectation) : this(
        "",
        expectation
    )

    fun verify(result: Result) {
        expectation.verify(result)
    }

    /**
     * Creates a new [ResultVerification] with a description.
     */
    fun withDescription(description: String) = expectation.withDescription(description)
}

/**
 * A functional interface representing a verification rule against a [Result].
 *
 * This is typically used in combination with an assertion framework (e.g., JUnit, AssertJ).
 * Implementations are expected to throw an [AssertionError] or any other exception
 * if the verification fails.
 *
 * Example using JUnit:
 * ```
 * val is200 = Expectation { result ->
 *     Assertions.assertEquals(200, result.statusCode)
 * }
 * ```
 *
 * You can also wrap this with a description using [withDescription].
 */
fun interface Expectation {
    fun verify(result: Result)
}

/**
 * Wraps this [Expectation] into a [ResultVerification] with the given [description].
 *
 * This allows for concise and expressive test definitions.
 *
 * Example:
 * ```
 * val isOk = Expectation { result -> assertEquals(200, result.statusCode) }
 * val check = isOk.withDescription("Should return 200 OK")
 * ```
 *
 * @param description human-readable description shown in logs or test reports
 * @return a [ResultVerification] combining this expectation with the description
 */
fun Expectation.withDescription(description: String) = ResultVerification(description, this)