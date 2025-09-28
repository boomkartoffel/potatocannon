package io.github.boomkartoffel.potatocannon.annotation

/**
 * Declares the **root object name** to use when serializing a type
 *
 * ### Interop
 * - The parameter is named `value`, so Java can use the short form: `@JsonRoot("User")`.
 *
 * ### Typical usage
 * ```kotlin
 * @JsonRoot("TestRoot")
 * data class Payload(val x: String = "1")
 * ```
 *
 * #### Serialized result with @JsonRoot
 * ```json
 * { "TestRoot": { "x": "1" } }
 * ```
 *
 * #### Serialized result without @JsonRoot
 * ```json
 * { "x": "1" }
 * ```
 *
 * @property value The wrapper element/property name to use as the root.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonRoot(val value: String)

/**
 * Overrides the external **JSON property name** used for (de)serialization of a field/property/
 * constructor parameter.
 *
 * This does not rename the Kotlin/Java symbol in your code—it only affects the wire representation.
 *
 * ### Interop
 * - The parameter is named `value`, so Java can use the short form: `@JsonName("userId")`.
 *
 * ### Typical usage
 * ```kotlin
 * data class User(
 *   @JsonName("userId") val id: Int,
 *   val name: String
 * )
 * // -> { "userId": 7, "name": "Max" }
 * ```
 *
 * ### With constructor parameters / records
 * Works on fields, properties, and primary-constructor parameters. For Java records,
 * annotate the record component:
 * ```java
 * record UserRecord(@JsonName("userId") int id, String name) {}
 * ```
 *
 * @property value The external name to use in the serialized representation.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonName(val value: String)

/**
 * Declares **additional accepted names** for a property during **deserialization**.
 * This is useful for backward/forward compatibility when field names change over time.
 *
 * During serialization, the primary name (possibly from {@link JsonName}) is used; aliases are ignored.
 *
 * ### Typical usage
 * ```kotlin
 * data class User(
 *   @JsonName("userId")
 *   @JsonAliases("uid", "id")
 *   val id: Int
 * )
 * // Accepts: {"userId": 7} or {"uid": 7} or {"id": 7}
 * ```
 *
 * ### Resolution rules (recommended)
 * - If both a primary name (`@JsonName`) and aliases are present in input, the first matching key wins.
 * - Libraries may define exact precedence; typically the primary name is preferred when multiple are present.
 *
 * @property value Alternative names accepted while reading input.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonAliases(vararg val value: String)

/**
 * Marks a property to be **ignored** for both serialization and deserialization.
 *
 * By default (`value = true`), the annotated member is excluded from the wire format.
 * If `value = false` is specified, consumers may interpret it as an explicit opt-in (useful
 * to override a class-level default that ignores members)—actual behavior depends on the mapper.
 *
 * ### Typical usage
 * ```kotlin
 * data class Secrets(
 *   val publicInfo: String,
 *   @JsonIgnore val password: String
 * )
 * // -> { "publicInfo": "..." }   // no 'password' field
 * ```
 *
 * ### Java / record example
 * ```java
 * record SecretsRecord(String publicInfo, @JsonIgnore String password) {}
 * ```
 *
 * @property value Whether the member should be ignored (`true`, default). Set to `false`
 *                 to explicitly **not** ignore (implementation-dependent).
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonIgnore(val value: Boolean = true)

/**
 * Controls the **serialization order** of properties on a class or record.
 *
 * Properties listed in `value` appear first and in the specified order. Properties not
 * listed should follow afterward (typically in declaration order), though exact behavior
 * may vary by mapper.
 *
 * ### Typical usage
 * ```kotlin
 * @JsonPropertyOrder("b", "a")
 * data class Ordered(val a: Int = 1, val b: Int = 2)
 * // -> {"b":2,"a":1}
 * ```
 *
 * ### Java / record example
 * ```java
 * @JsonPropertyOrder({"b", "a"})
 * record OrderedRecord(int a, int b) {}
 * ```
 *
 * @property value The ordered list of property names to place first in the output.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonPropertyOrder(vararg val value: String)

/**
 * Omits properties whose value is **`null`** from the serialized output.
 *
 * Can be applied at:
 * - **Class level**: applies to all properties of the class unless a property overrides it.
 * - **Property/parameter/field level**: applies only to the annotated member.
 *
 * Property-level annotations should override class-level behavior.
 *
 * ### Typical usage (property level)
 * ```kotlin
 * data class Example(
 *   val x: String,
 *   @JsonOmitNull val z: String? = null
 * )
 * // -> {"x":"..."}   // 'z' omitted when null
 * ```
 *
 * ### Class level
 * ```kotlin
 * @JsonOmitNull
 * data class ExampleAll(val x: String? = null, val y: String? = null)
 * // -> {}            // both omitted when null
 * ```
 *
 * ### Notes
 * - Non-nullable properties in Kotlin cannot be `null` at runtime, so this mainly affects
 *   `T?` types or Java reference types.
 * - Setting `value = false` can force inclusion of `null` for a member on a class where
 *   nulls are omitted by default (mapper-dependent).
 *
 * @property value Whether to omit `null` values (`true`, default). Set to `false` to include `null`s.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonOmitNull(val value: Boolean = true)