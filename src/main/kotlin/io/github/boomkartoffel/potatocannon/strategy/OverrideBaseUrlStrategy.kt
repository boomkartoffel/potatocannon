package io.github.boomkartoffel.potatocannon.strategy
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.cannon.Cannon

/**
 * Overrides the base URL for a specific [Potato].
 *
 * Useful for scenarios where a shared [Cannon] setting is used, but one or more requests
 * need to target a different base URL. For example, retrieving a token from an external service
 * before executing the main requests.
 *
 * If multiple [OverrideBaseUrl] settings are provided, the **last one** is used.
 * This does not affect other [Potato] fired by the same [Cannon].
 *
 * @property url The alternate base URL to use instead of the cannon's default.
 * @since 0.1.0
 */
class OverrideBaseUrl(val url: String): PotatoSetting