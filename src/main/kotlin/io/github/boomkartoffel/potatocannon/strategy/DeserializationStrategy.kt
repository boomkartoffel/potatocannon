package io.github.boomkartoffel.potatocannon.strategy
import io.github.boomkartoffel.potatocannon.deserialization.EnumDefaultValue


/**
 * Configurations that influence how the response is deserialized.
 *
 * These are [CannonConfiguration]s, so they apply globally to the whole cannon run,
 * unless overridden by [PotatoConfiguration] at the request level.
 *
 * Defaults:
 * - [UnknownPropertyMode] → [UnknownPropertyMode.IGNORE]
 * - [NullCoercion] → [NullCoercion.STRICT]
 * - All other feature toggles → disabled
 *
 * @since 0.1.0
 */
sealed interface DeserializationStrategy : CannonConfiguration, PotatoConfiguration

/**
 * Determines how unknown properties in JSON payloads are handled.
 *
 * - [IGNORE] (default): Extra fields in the JSON that are not present in the target
 *   data class will be silently ignored.
 * - [FAIL]: Deserialization fails with an exception if an unknown property is encountered.
 *
 * @since 0.1.0
 */
enum class UnknownPropertyMode : DeserializationStrategy {
    IGNORE,
    FAIL
}

/**
 * Determines how `null` values are treated when mapped into Kotlin properties.
 *
 * - [STRICT] (default): Honors Kotlin nullability — if a non-nullable property
 *   receives `null`, deserialization will fail. Collections and maps remain `null`
 *   if the payload says `null`.
 * - [RELAX]: Nulls are coerced into defaults:
 *   - `null` collection → `emptyList` / `emptySet`
 *   - `null` map → `emptyMap`
 *   - `null` property with a default value in the data class → default is applied
 *   - `null` property without a default value → deserialization fails
 *
 * @since 0.1.0
 */
enum class NullCoercion : DeserializationStrategy {
    STRICT,
    RELAX
}

/**
 * Enables proper deserialization of Java 8+ date/time types (`java.time.*`)
 * such as `LocalDate`, `Instant`, and `ZonedDateTime`.
 *
 * Disabled by default.
 *
 * @since 0.1.0
 */
object JavaTimeSupport : DeserializationStrategy

/**
 * Makes property name matching case-insensitive.
 *
 * For example, both `{"userName": "x"}` and `{"USERNAME": "x"}` will populate
 * the `userName` property in a Kotlin data class.
 *
 * Disabled by default.
 *
 * @since 0.1.0
 */
object CaseInsensitiveProperties : DeserializationStrategy

/**
 * Allows empty strings (`""`) in the JSON payload to be treated as `null`
 * for object values.
 *
 * Example: `{"user": ""}` → `user = null`.
 *
 * Disabled by default.
 *
 * @since 0.1.0
 */
object AcceptEmptyStringAsNullObject : DeserializationStrategy

/**
 * Allows enum deserialization to fall back to a default constant if an unknown
 * enum value is encountered.
 *
 * To use this, one enum constant must be annotated with
 * [EnumDefaultValue]
 *
 * Disabled by default.
 *
 * @since 0.1.0
 */
object UnknownEnumAsDefault : DeserializationStrategy