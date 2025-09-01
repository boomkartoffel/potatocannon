package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.boomkartoffel.potatocannon.deserialization.EnumDefaultDeserializationModule

import io.github.boomkartoffel.potatocannon.strategy.AcceptEmptyStringAsNullObject
import io.github.boomkartoffel.potatocannon.strategy.CaseInsensitiveProperties
import io.github.boomkartoffel.potatocannon.strategy.DeserializationStrategy
import io.github.boomkartoffel.potatocannon.strategy.JavaTimeSupport
import io.github.boomkartoffel.potatocannon.strategy.NullCoercion
import io.github.boomkartoffel.potatocannon.strategy.UnknownEnumAsDefault
import io.github.boomkartoffel.potatocannon.strategy.UnknownPropertyMode

/**
 * Strategy interface for deserializing objects from a raw string.
 *
 * Implementations define how to convert serialized data (e.g. JSON, XML, CSV) into a Kotlin or Java object
 * of the specified class.
 *
 * Example usage (with a JSON implementation):
 * ```
 * val user: User = jsonDeserializer.deserialize(data, User::class.java)
 * ```
 *
 */
interface Deserializer {
    /**
     * Deserialize the given string into an instance of the specified class.
     *
     * @param T the target type to deserialize into
     * @param data the raw serialized input (e.g. JSON string)
     * @param targetClass the class to deserialize into
     * @return the deserialized object of type [T]
     * @since 0.1.0
     */
    fun <T> deserializeObject(data: String, targetClass: Class<T>): T

    /**
     * Deserialize the given string into a list of instances of the specified class.
     *
     * @param T the target element type to deserialize into
     * @param data the raw serialized input (e.g. JSON array string)
     * @param targetClass the class to deserialize each element into
     * @return a list of deserialized objects of type [T]
     * @since 0.1.0
     */
    fun <T> deserializeList(data: String, targetClass: Class<T>): List<T>
}


// Defaults:
// - Nulls: strict (respect Kotlin nullability)
// - Unknown properties: ignored (library-friendly)
// - Other toggles: off unless specified
private fun buildMapper(strategies: List<DeserializationStrategy>, format: DeserializationFormat): ObjectMapper {
    var nullCoercion: NullCoercion = NullCoercion.STRICT
    var unknownProps: UnknownPropertyMode = UnknownPropertyMode.IGNORE
    var javaTime = false
    var caseInsensitive = false
    var emptyStringAsNull = false
    var unknownEnumAsDefault = false

    strategies.forEach { s ->
        when (s) {
            is NullCoercion -> nullCoercion = s
            is UnknownPropertyMode -> unknownProps = s
            JavaTimeSupport -> javaTime = true
            CaseInsensitiveProperties -> caseInsensitive = true
            AcceptEmptyStringAsNullObject -> emptyStringAsNull = true
            UnknownEnumAsDefault -> unknownEnumAsDefault = true
        }
    }

    val kotlinModuleBuilder = KotlinModule.Builder()
        .configure(KotlinFeature.SingletonSupport, true)

    when (nullCoercion) {
        NullCoercion.STRICT ->
            kotlinModuleBuilder.configure(KotlinFeature.NewStrictNullChecks, true)

        NullCoercion.RELAX ->
            kotlinModuleBuilder
                .configure(KotlinFeature.NewStrictNullChecks, false)
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, true)
    }

    val builder = when (format) {
        DeserializationFormat.JSON -> JsonMapper.builder()
        DeserializationFormat.XML -> XmlMapper.builder()
    }

    builder.addModule(kotlinModuleBuilder.build())

    when (unknownProps) {
        UnknownPropertyMode.IGNORE -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        UnknownPropertyMode.FAIL -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    if (javaTime) builder.addModule(JavaTimeModule())
    if (caseInsensitive) builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    if (emptyStringAsNull) builder.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
    if (unknownEnumAsDefault) {
        builder.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        builder.addModule(EnumDefaultDeserializationModule)
    }

    return builder.build()
}

internal class JsonDeserializer(deserializationStrategies: List<DeserializationStrategy>) : Deserializer {
    internal val mapper: ObjectMapper = buildMapper(deserializationStrategies, DeserializationFormat.JSON)


    override fun <T> deserializeObject(data: String, targetClass: Class<T>): T {
        return mapper.readValue(data, targetClass)
    }

    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, targetClass)
        return mapper.readValue(data, listType)
    }
}

internal class XmlDeserializer(deserializationStrategies: List<DeserializationStrategy>) : Deserializer {
    val mapper: ObjectMapper = buildMapper(deserializationStrategies, DeserializationFormat.XML)


    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, targetClass)
        return mapper.readValue(data, listType)
    }

    override fun <T> deserializeObject(data: String, targetClass: Class<T>): T {
        return mapper.readValue(data, targetClass)
    }
}

/**
 * This enum is only relevant when using built-in JSON or XML deserializers.
 * Indicates which format should be used when deserializing response bodies
 * using the library’s built-in default mappers.
 *
 * @see io.github.boomkartoffel.potatocannon.result.Result.bodyAsObject
 * @see io.github.boomkartoffel.potatocannon.result.Result.bodyAsList
 * @since 0.1.0
 */
enum class DeserializationFormat {
    /**
     * Built-in JSON deserialization using a default Jackson ObjectMapper.
     */
    JSON,

    /**
     * Built-in XML deserialization using a default Jackson XmlMapper.
     */
    XML,
}