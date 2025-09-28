package io.github.boomkartoffel.potatocannon.marshalling

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.boomkartoffel.potatocannon.annotation.JsonRoot
import io.github.boomkartoffel.potatocannon.strategy.*

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
private fun buildMapper(strategies: List<DeserializationStrategy>, format: WireFormat): ObjectMapper {
    var nullCoercion: NullCoercion = NullCoercion.STRICT
    var unknownProps: UnknownPropertyMode = UnknownPropertyMode.IGNORE
    var caseInsensitiveProperties = false
    var caseInsensitiveEnums = false
    var emptyStringAsNull = false
    var unknownEnumAsDefault = false

    strategies.forEach { s ->
        when (s) {
            is NullCoercion -> nullCoercion = s
            is UnknownPropertyMode -> unknownProps = s
            CaseInsensitiveProperties -> caseInsensitiveProperties = true
            AcceptEmptyStringAsNullObject -> emptyStringAsNull = true
            UnknownEnumAsDefault -> unknownEnumAsDefault = true
            CaseInsensitiveEnums -> caseInsensitiveEnums = true
        }
    }

    val kotlinModuleBuilder = KotlinModule.Builder()
        .configure(KotlinFeature.SingletonSupport, true)

    when (nullCoercion) {
        NullCoercion.STRICT ->
            kotlinModuleBuilder.configure(KotlinFeature.StrictNullChecks, true)

        NullCoercion.RELAX ->
            kotlinModuleBuilder
                .configure(KotlinFeature.StrictNullChecks, false)
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, true)
    }

    val builder = when (format) {
        WireFormat.JSON -> JsonMapper.builder()
        WireFormat.XML -> XmlMapper.builder()
    }

    builder.addModule(kotlinModuleBuilder.build())
    builder.addModule(JsonAnnotationModule)
    builder.addModule(XmlAnnotationModule)

    when (unknownProps) {
        UnknownPropertyMode.IGNORE -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        UnknownPropertyMode.FAIL -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    //JavaTimeModule enabled by default
    builder.addModule(JavaTimeModule())

    if (caseInsensitiveProperties) builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    if (caseInsensitiveEnums) builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    if (emptyStringAsNull) builder.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
    if (unknownEnumAsDefault) {
        builder.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        builder.addModule(EnumDefaultDeserializationModule)
    }

    return builder.build()
}

internal class JsonDeserializer(deserializationStrategies: List<DeserializationStrategy>) : Deserializer {
    internal val mapper: ObjectMapper = buildMapper(deserializationStrategies, WireFormat.JSON)


    override fun <T> deserializeObject(data: String, targetClass: Class<T>): T {
        return mapper.readValueRootAware(data, targetClass)
    }

    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        return mapper.readListRootAware(data, targetClass)
    }
}

internal class XmlDeserializer(deserializationStrategies: List<DeserializationStrategy>) : Deserializer {
    val mapper: ObjectMapper = buildMapper(deserializationStrategies, WireFormat.XML)


    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, targetClass)
        return mapper.readValue(data, listType)

    }

    override fun <T> deserializeObject(data: String, targetClass: Class<T>): T {
        return mapper.readValue(data, targetClass)
    }
}

private fun <T> ObjectMapper.readValueRootAware(json: String, target: Class<T>): T {
    val hasRoot = target.isAnnotationPresent(JsonRoot::class.java)
    val reader = if (hasRoot) {
        readerFor(target).with(DeserializationFeature.UNWRAP_ROOT_VALUE)
    } else {
        readerFor(target).without(DeserializationFeature.UNWRAP_ROOT_VALUE)
    }
    return reader.readValue(json)
}


private fun <T> ObjectMapper.readListRootAware(json: String, target: Class<T>): List<T> {
    val node = readTree(json)

    require(node.isArray) { "Expected JSON array for List<$target>, got: ${node.nodeType}" }

    val arr = node as ArrayNode
    return arr.map { elem ->
        val elemJson = writeValueAsString(elem)
        readValueRootAware(elemJson, target)
    }
}