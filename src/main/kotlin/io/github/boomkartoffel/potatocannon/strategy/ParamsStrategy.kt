package io.github.boomkartoffel.potatocannon.strategy

/**
 * Adds a query parameter to the request URL.
 *
 * This configuration can be applied to individual Potatoes or globally to the Cannon.
 * Multiple query parameters with the same key are allowed and will be appended.
 *
 * @param key the name of the query parameter.
 * @param value the value associated with the key.
 * @since 0.1.0
 */
class QueryParam(val key: String, val value: String) : PotatoConfiguration, CannonConfiguration {
    fun apply(queryParams: MutableMap<String, List<String>>) {
        if (!queryParams.containsKey(key)) {
            queryParams[key] = mutableListOf(value)
        } else {
            queryParams[key] = queryParams[key]!!.toMutableList().apply { add(value) }
        }
    }
}