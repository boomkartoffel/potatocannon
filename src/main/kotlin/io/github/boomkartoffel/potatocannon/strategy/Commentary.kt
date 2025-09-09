package io.github.boomkartoffel.potatocannon.strategy

/**
 * A comment that is printed above a request in the Potato Dispatch Log.
 *
 * Use this to annotate runs with context (e.g., why a test exists, what is being validated,
 * ticket numbers, or scenario names). It can be applied at **cannon** scope and/or at
 * **potato** scope.
 *
 * **Ordering / precedence**
 * - All commentary entries are printed.
 * - **Cannon-level** comments are emitted first, in the order they were added to the cannon’s configuration.
 * - **Potato-level** comments are emitted next, in the order they were added to the potato’s configuration.
 * - There is no de-duplication or overriding; everything you add is shown.
 *
 * Example output line:
 * `| ℹ️ Warm-up run to prime caches`
 *
 * @property message The commentary text to be shown in the log.
 * @since 0.1.0
 */
class LogCommentary(val message: String) : PotatoSetting, CannonSetting