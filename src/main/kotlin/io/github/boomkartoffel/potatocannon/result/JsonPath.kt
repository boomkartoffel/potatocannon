package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.boomkartoffel.potatocannon.exception.JsonPathTypeException
import com.fasterxml.jackson.databind.JsonNode as JacksonNode


/**
 * Strict, fail-fast wrapper around a node selected by [Result.jsonPath].
 *
 * ### Access
 * - Scalars: `asText()`, `asInt()`, `asLong()`, `asDouble()`, `asBoolean()` — all **throw** if the node is missing
 *   or not the exact JSON type (no coercion; e.g., `"42"` is **not** an int).
 * - Arrays: `asArray()` to get a [PathMatchArray] (strict), or use `get[index]` directly (throws if not an array).
 * - Objects and arrays-of-objects: `get["field"]`:
 *   - If this node is an **object**, returns that field (throws if missing).
 *   - If this node is an **array of objects**, **projects** the field across all elements and returns an **array**
 *     of those values. It fails fast if any element isn’t an object or if the field is missing.
 *
 * ### Projections after filters
 * Projecting a field after a filter (e.g., `$.nested[?(@.check == 2)].value`) returns a **result list**.
 * Jayway does not reliably support indexing that projected list *inside* the path (i.e., `...].value[0]` can be unreliable).
 *
 * With the strict API you can:
 *  1) project in the JSONPath, then turn the result into an array via `asArray()` and index in code, or
 *  2) keep objects in the JSONPath, index the filtered array, and then access the field via `["field"]`.
 *
 * Example payload:
 * {
 *   "nested": [
 *     { "check": 1, "value": "a" },
 *     { "check": 2, "value": "b" },
 *     { "check": 3, "value": "c" }
 *   ]
 * }
 *
 * Examples:
 * // Project values, then pick first match (result list -> array -> index -> scalar)
 * result.jsonPath("$.nested[?(@.check == 2)].value")
 *       .asArray()[0]
 *       .asText()                           // -> "b"
 *
 * // Keep objects, then index, then access a field (array -> index -> object field projection)
 * result.jsonPath("$.nested[?(@.check == 2)]")
 *       [0]["value"]
 *       .asText()                           // -> "b"
 *
 * ### General rules
 * - Arrays: use `index` directly (throws if not an array), or call `asArray()` if you prefer explicit narrowing.
 * - Objects: call `asObject()` when you need object-specific helpers (`size()`, `contains(field)`,
 *   `keys()`). It throws a [JsonPathTypeException] if the node isn’t an object (or is `null`).
 *   Field access via `["field"]` still works without narrowing.
 * - Objects / arrays-of-objects: use `["field"]`. On an object it returns that field; on an array it **projects**
 *   the field and returns an array result (strictly fails on shape/field mismatches).
 * - Scalars: read with `asText()` / `asInt()` / `asBoolean()` / etc. (no coercion).
 */
class PathMatch internal constructor(
    internal val node: JacksonNode?,
    internal val expr: String
) {
    private fun ensurePresent(): JacksonNode =
        node ?: throw JsonPathTypeException("Expected value at $expr but was missing")

    private fun typeName(n: JacksonNode) = when {
        n.isNull -> "null"; n.isTextual -> "text"; n.isNumber -> "number"; n.isBoolean -> "boolean"
        n.isArray -> "array"; n.isObject -> "object"; else -> "unknown"
    }

    /**
     * Returns the value as a JSON string.
     *
     * No coercion is performed (e.g., numbers/booleans are not converted to strings).
     *
     * @return the textual value
     * @throws JsonPathTypeException if the node is absent or not textual.
     */
    fun asText(): String {
        val n = ensurePresent()
        if (n.isTextual) return n.asText()
        throw JsonPathTypeException("Expected text at $expr but was ${typeName(n)}")
    }

    /**
     * Returns the value as an `Int`.
     *
     * Accepts integral numbers that can be represented as a 32-bit integer.
     * No string-to-number coercion is performed.
     *
     * @return the integer value
     * @throws JsonPathTypeException if the node is absent, non-numeric, or out of range for `Int`.
     */
    fun asInt(): Int {
        val n = ensurePresent()
        return when {
            n.isInt -> n.intValue()
            n.isIntegralNumber && n.canConvertToInt() -> n.intValue()
            else -> throw JsonPathTypeException("Expected int at $expr but was ${typeName(n)}")
        }
    }

    /**
     * Returns the value as a `Long`.
     *
     * Accepts integral numbers that can be represented as a 64-bit integer.
     * No string-to-number coercion is performed.
     *
     * @return the long value
     * @throws JsonPathTypeException if the node is absent, non-numeric, or out of range for `Long`.
     */
    fun asLong(): Long {
        val n = ensurePresent()
        return when {
            n.isLong -> n.longValue()
            n.isIntegralNumber && n.canConvertToLong() -> n.longValue()
            else -> throw JsonPathTypeException("Expected long at $expr but was ${typeName(n)}")
        }
    }

    /**
     * Returns the value as a `Double`.
     *
     * Accepts any numeric JSON value. No string-to-number coercion is performed.
     *
     * @return the double value
     * @throws JsonPathTypeException if the node is absent or not numeric.
     */
    fun asDouble(): Double {
        val n = ensurePresent()
        if (n.isNumber) return n.doubleValue()
        throw JsonPathTypeException("Expected double at $expr but was ${typeName(n)}")
    }

    /**
     * Returns the value as a `Boolean`.
     *
     * No coercion is performed (e.g., `"true"`/`"false"` strings are not accepted).
     *
     * @return the boolean value
     * @throws JsonPathTypeException if the node is absent or not a JSON boolean.
     */
    fun asBoolean(): Boolean {
        val n = ensurePresent()
        if (n.isBoolean) return n.booleanValue()
        throw JsonPathTypeException("Expected boolean at $expr but was ${typeName(n)}")
    }

    /**
     * Narrows the value to a JSON array and returns an indexed view.
     *
     * @return a [PathMatchArray] wrapper for indexed access
     * @throws JsonPathTypeException if the node is absent or not an array.
     */
    fun asArray(): PathMatchArray {
        val n = ensurePresent()
        if (!n.isArray) throw JsonPathTypeException("Expected array at $expr but was ${typeName(n)}")
        return PathMatchArray(n as ArrayNode, expr)
    }

    /**
     * Indicates whether the value is missing (i.e., the path did not resolve to any node).
     */
    fun isMissing(): Boolean = node == null

    /**
     * Indicates whether the value is present (the opposite of [isMissing]).
     */
    fun isPresent(): Boolean = node != null

    /**
     * Indicates whether the value is JSON `null`.
     *
     * Returns `false` if the value is missing.
     */
    fun isNull(): Boolean = node?.isNull ?: false

    /**
     * Strict array indexing on this node.
     *
     * @param index zero-based position
     * @return a [PathMatch] for the element at [index]
     * @throws JsonPathTypeException if the node is not an array, or if [index] is out of bounds.
     */
    operator fun get(index: Int): PathMatch {
        val n = ensurePresent()
        if (!n.isArray) throw JsonPathTypeException("Expected array at $expr but was ${typeName(n)}")
        val arr = n as ArrayNode
        if (index !in 0 until arr.size())
            throw JsonPathTypeException("Index $index out of bounds (size=${arr.size()}) at $expr")
        return PathMatch(arr.get(index), "$expr -> Index: $index")
    }

    /**
     * Narrows the value to a JSON object and returns an object-specific view.
     *
     * @return a [PathMatchObject] wrapper for field access and helpers
     * @throws JsonPathTypeException if the node is absent or not an object.
     */
    fun asObject(): PathMatchObject {
        val n = ensurePresent()
        if (!n.isObject) throw JsonPathTypeException("Expected object at $expr but was ${typeName(n)}")
        else return PathMatchObject(n as ObjectNode, expr)
    }

    /**
     * Field access on an object or projection over an array of objects.
     *
     * Semantics:
     * - **Object:** returns the named field (throws if missing).
     * - **Array of objects:** projects the field across all elements and returns an array of the values.
     *   Strict shape checks apply:
     *   - If any element is not an object, an exception is thrown identifying the element index.
     *   - If the field is missing on any element, an exception is thrown identifying the element index.
     * - **Other types:** throws, since only objects and arrays-of-objects support field access.
     *
     * @param field the field name to access/project
     * @return a [PathMatch] pointing to the field value (object case) or to an array of values (projection case)
     * @throws JsonPathTypeException on shape/type mismatches or missing fields, with precise location info.
     */
    operator fun get(field: String): PathMatch {
        val n = ensurePresent()
        return when {
            n.isObject -> {
                val obj = n as ObjectNode
                val v = obj.get(field) ?: throw JsonPathTypeException(
                    "Expected field '$field' at $expr but was missing"
                )
                PathMatch(v, "$expr -> Field: '$field'")
            }

            n.isArray -> {
                val arr = n as ArrayNode
                val out: ArrayNode = JsonNodeFactory.instance.arrayNode(arr.size())
                var i = 0
                val it = arr.elements()
                while (it.hasNext()) {
                    val e = it.next()
                    if (!e.isObject) {
                        throw JsonPathTypeException(
                            "Expected array of objects at $expr but element #$i was ${typeName(e)}"
                        )
                    }
                    val v = (e as ObjectNode).get(field) ?: throw JsonPathTypeException(
                        "Expected field '$field' on element #$i at $expr but was missing"
                    )
                    out.add(v)
                    i++
                }
                PathMatch(out, "$expr -> Project Field: '$field'")
            }

            else -> throw JsonPathTypeException("Expected object or array at $expr but was ${typeName(n)}")
        }
    }
}

/**
 * Read-only view of a JSON array within the JsonPath [PathMatch].
 *
 * Use this wrapper to index into arrays while preserving strict error semantics
 * and precise locations in exception messages.
 *
 * Capabilities:
 * - [size] — returns the number of elements.
 * - [get] — retrieves the element at a zero-based index; throws [JsonPathTypeException]
 *   with the JSONPath location when the index is out of bounds.
 * - [first] — retrieves the first element; throws [JsonPathTypeException] if the array is empty.
 *
 * Notes:
 * - Indices are zero-based and validated against the current array length.
 * - Elements may be any JSON type (object, array, scalar, or `null`); returned values are wrapped
 *   as [PathMatch] for further navigation or type-safe reads.
 *
 * Example:
 * ```
 * val items = result.jsonPath("$.items").asArray()
 * items.size() shouldBe 3
 * val secondName = items[1]["name"].asText()
 * val firstId = items.first()["id"].asLong()
 * ```
 *
 * @since 0.1.0
 */
class PathMatchArray internal constructor(
    private val arr: ArrayNode,
    private val expr: String
) {
    /**
     * Number of elements in this array.
     *
     * @return element count (zero for an empty array)
     */
    fun size(): Int = arr.size()

    /**
     * Element at the given zero-based [index] as a [PathMatch].
     *
     * The returned value may represent any JSON type (object, array, scalar, or `null`).
     *
     * @param index zero-based element position
     * @return a [PathMatch] positioned at the requested element
     * @throws JsonPathTypeException if [index] is out of bounds.
     */
    operator fun get(index: Int): PathMatch {
        if (index !in 0 until arr.size())
            throw JsonPathTypeException("Index $index out of bounds (size=${arr.size()}) at $expr")
        return PathMatch(arr.get(index), "$expr -> Index: $index")
    }

    /**
     * Returns the first element of this array as a [PathMatch].
     *
     * Useful as a convenience when the array is expected to be non-empty.
     *
     * @return a [PathMatch] positioned at index 0
     * @throws JsonPathTypeException if the array is empty.
     */
    fun first(): PathMatch =
        if (arr.size() > 0) PathMatch(arr.get(0), "$expr -> Index: 0")
        else throw JsonPathTypeException("Expected non-empty array at $expr but was empty")
}

/**
 * Read-only view of a JSON object within the JsonPath [PathMatch].
 *
 * Instances are produced by [PathMatch.asObject] to expose object-specific helpers while preserving
 * rich error messages that include the originating JSONPath expression.
 *
 * ### Semantics
 * - **Presence vs. null:** A field that exists with value JSON `null` is considered *present*. It counts
 *   toward [size], appears in [keys], and makes [contains] return `true`.
 * - **Type safety:** Narrowing to an object is performed by [PathMatch.asObject]. If the underlying node
 *   is not an object (or is `null`), that method throws [JsonPathTypeException] explaining the location.
 * - **Field access:** Using the indexing operator ([get]) on this wrapper throws if the field is missing.
 *   The exception message includes the JSONPath location and expected shape.
 * - **Immutability:** This wrapper does not modify the underlying JSON.
 *
 * ### Examples
 * ```
 * val obj = result.jsonPath("$.user").asObject()
 * val id  = obj["id"].asLong()
 * val hasMiddleName = obj.contains("middleName") // true even if the value is JSON null
 * val count = obj.size()                         // number of fields
 * val names = obj.keys()                         // list of field names
 * ```
 *
 * @since 0.1.0
 */
class PathMatchObject internal constructor(
    private val obj: ObjectNode,
    private val expr: String
) {

    /**
     * Returns the number of fields (keys) in this object.
     *
     * Counting rules:
     * - Fields whose value is JSON `null` **are included**.
     * - JSON objects do not contain duplicate keys by definition.
     *
     * @return the number of fields present on this object
     */
    fun size(): Int = obj.size()

    /**
     * Returns the value of the given [field] wrapped as a [PathMatch].
     *
     * The returned [PathMatch] may point to any JSON type (object, array, scalar, or `null`).
     *
     * @param field the field name to access
     * @return a [PathMatch] positioned at the value of [field]
     * @throws JsonPathTypeException if the field is **missing** from the object.
     */
    operator fun get(field: String): PathMatch {
        val v = obj.get(field)
            ?: throw JsonPathTypeException("Expected field '$field' at $expr but was missing")
        return PathMatch(v, "$expr -> Field: $field")
    }

    /**
     * Checks whether this object contains a field named [field].
     *
     * Presence semantics:
     * - Returns `true` even if the field's value is JSON `null`
     *
     * @param field the field name to test
     * @return `true` if the field exists (regardless of its value), otherwise `false`
     */
    fun contains(field: String): Boolean = obj.has(field)

    /**
     * All field names on this object as a list.
     *
     * @return a list of field names present on this object
     */
    fun keys(): List<String> = obj.fieldNames().asSequence().toList()
}


