package io.github.boomkartoffel.potatocannon.strategy

/**
 * Marker interface for configurations that apply to individual Potatoes (HTTP requests).
 *
 * Implementations of this interface can define headers, query parameters, body settings,
 * verifications, or logging preferences that are specific to a single request.
 *
 * Examples:
 * - [QueryParam]
 * - [CustomHeader]
 * - [Expectation]
 *
 * For configurations that can only be applied once (e.g., logging, auth headers), the last one will be applied, i.e. PotatoConfiguration will override CannonConfiguration.

 * @since 0.1.0
 */
sealed interface PotatoSetting

/**
 * Marker interface for configurations that apply globally to the Cannon (test runner).
 *
 * Implementations of this interface define settings such as execution mode (sequential/parallel),
 * shared headers, global verifications, or logging behavior that affect all fired requests.
 *
 * Examples:
 * - [FireMode]
 * - [Logging]
 * - [Expectation]
 *
 * For configurations that can only be applied once (e.g., logging, auth headers), the last one will be applied, i.e. PotatoConfiguration will override CannonConfiguration.
 *
 * @since 0.1.0
 */
sealed interface CannonSetting