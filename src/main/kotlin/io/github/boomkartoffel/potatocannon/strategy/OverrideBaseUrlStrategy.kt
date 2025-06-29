package io.github.boomkartoffel.potatocannon.strategy

/**
 * Overrides the base URL for a specific [Potato].
 *
 * Useful for scenarios where a shared [Cannon] configuration is used, but one or more requests
 * need to target a different base URL. For example, retrieving a token from an external service
 * before executing the main requests.
 *
 * If multiple [OverrideBaseUrl] configurations are provided, the **last one** is used.
 * This does not affect other potatoes fired by the same cannon.
 *
 * @property url The alternate base URL to use instead of the cannon's default.
 */
class OverrideBaseUrl(val url: String): PotatoConfiguration