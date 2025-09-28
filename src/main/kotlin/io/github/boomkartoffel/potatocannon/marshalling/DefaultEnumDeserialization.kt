package io.github.boomkartoffel.potatocannon.marshalling

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
import io.github.boomkartoffel.potatocannon.strategy.UnknownEnumAsDefault

/**
 * Marks a single enum constant as the default value to use during deserialization
 * when an unknown or invalid value is encountered.
 *
 * For example, given:
 * ```
 * enum class Status {
 *     @EnumDefaultValue
 *     UNKNOWN,
 *     ACTIVE,
 *     INACTIVE
 * }
 * ```
 *
 * If the input contains `"ACTIVE"`, the value is `Status.ACTIVE`.
 * If the input contains an unrecognized string (e.g. `"DELETED"`), or an empty
 * string then the status will be set to `Status.UNKNOWN`.
 *
 * Exactly one enum constant in a type should be annotated with `@EnumDefaultValue` (otherwise the first constant will be used).
 *
 * This annotation is recognized only when the
 * [UnknownEnumAsDefault] deserialization strategy is enabled.
 *
 * @since 0.1.0
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnumDefaultValue

internal class EnumAnnotationIntrospector : NopAnnotationIntrospector() {

    override fun findDefaultEnumValue(enumCls: Class<Enum<*>>): Enum<*>? {
        val values = enumCls.enumConstants ?: return null
        for (ev in values) {
            val field = try { enumCls.getField(ev.name) } catch (_: NoSuchFieldException) { continue }
            if (field.isAnnotationPresent(EnumDefaultValue::class.java)) {
                return ev
            }
        }
        return null
    }
}

internal object EnumDefaultDeserializationModule : Module() {
    override fun version(): Version = Version.unknownVersion()
    override fun getModuleName(): String = "EnumDefaultValueModule"
    override fun setupModule(ctx: SetupContext) {
        ctx.insertAnnotationIntrospector(EnumAnnotationIntrospector())
    }
}