package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.result.Result

/**
 * A configuration that defines how a result should be verified after a request is fired.
 *
 * Combines a [Check] with an optional human-readable [description] for logging.
 *
 * This configuration can be applied to individual Potatoes or globally to the Cannon.
 *
 * @param check the condition that should be met by the [Result].
 * @param description an optional description of the verification. This text will be shown in the log when the verification is executed.
 * @since 0.1.0
 */
class Expectation(val description: String, private val check: Check) : PotatoConfiguration,
    CannonConfiguration {

    constructor(check: Check) : this(
        "",
        check
    )

    internal fun verify(result: Result): ExpectationResult {
        try {
            check.check(result)
            return ExpectationResult(this, null, false)
        } catch (ae: AssertionError) {
            return ExpectationResult(this, ae, true)
        } catch (e: Throwable) {
            // Catch all other exceptions to ensure we always return an ExpectationResult
            return ExpectationResult(this, e, false)
        }
    }
}

internal class ExpectationResult(val expectation: Expectation, val error: Throwable?, val isAssertionError: Boolean)


/**
 * A functional interface representing a verification rule against a [Result].
 *
 * This is typically used in combination with an assertion framework (e.g., JUnit, AssertJ).
 * Implementations are expected to throw an [AssertionError] or any other exception
 * if the verification fails.
 *
 * Example using JUnit:
 * ```
 * val is200 = Check { result ->
 *     assertEquals(200, result.statusCode)
 * }
 * ```
 *
 * You can also wrap this with a description using [withDescription].
 *
 * @since 0.1.0
 */
fun interface Check : PotatoConfiguration, CannonConfiguration {
    fun check(result: Result)
}

/**
 * Wraps this [Check] into an [Expectation] with the given [description].
 *
 * This allows for concise and expressive test definitions.
 *
 * Example:
 * ```
 * val isOk = Check { result -> assertEquals(200, result.statusCode) }
 * val isOkExpectation = isOk.withDescription("Should return 200 OK")
 * ```
 *
 * @param description human-readable description shown in the Potato Dispatch log
 * @return an [Expectation] combining this check with the description
 * @since 0.1.0
 */
fun Check.withDescription(description: String) = Expectation(description, this)