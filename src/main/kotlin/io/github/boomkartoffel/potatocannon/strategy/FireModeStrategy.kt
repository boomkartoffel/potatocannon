package io.github.boomkartoffel.potatocannon.strategy


/**
 * Defines how the cannon fires its potatoes (requests).
 *
 * This controls whether the requests are sent one after another (sequentially)
 * or simultaneously (in parallel). If multiple FireModes are specified, then the last provided one will be used.
 *
 * The default behavior is to fire all requests in parallel.
 */
enum class FireMode : CannonConfiguration {

    /**
     * Sends requests one at a time, in the order the potatoes are provided in the list.
     *
     * Useful for deterministic testing, debugging, or when later requests depend
     * on the results of earlier ones.
     */
    SEQUENTIAL,

    /**
     * Sends all requests simultaneously using a thread pool.
     *
     * Ideal for stress testing, concurrency validation, and simulating
     * real-world concurrent traffic.
     *
     * Requests may complete in any order, depending on network conditions
     *
     * This mode is the default if no FireMode is specified in the configuration.
     */
    PARALLEL
}
