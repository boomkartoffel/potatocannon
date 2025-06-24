package io.github.boomkartoffel.potatocannon.result

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature

/**
 * Strategy interface for deserializing a single object from a raw string.
 *
 * Implementations define how to convert serialized data (e.g. JSON, XML, CSV) into a Kotlin or Java object
 * of the specified class.
 *
 * Example usage (with a JSON implementation):
 * ```
 * val user: User = jsonDeserializer.deserializeSingle(data, User::class.java)
 * ```
 *
 * @see ListDeserializer for deserializing lists
 */
interface SingleDeserializer {
    /**
     * Deserialize the given string into an instance of the specified class.
     *
     * @param T the target type to deserialize into
     * @param data the raw serialized input (e.g. JSON string)
     * @param targetClass the class to deserialize into
     * @return the deserialized object of type [T]
     */
    fun <T> deserializeSingle(data: String, targetClass: Class<T>): T
}

/**
 * Strategy interface for deserializing a list of objects from a raw string.
 *
 * Implementations define how to convert serialized data (e.g. JSON array, XML list, CSV rows)
 * into a list of Kotlin or Java objects of the specified class.
 *
 * Example usage (with a JSON implementation):
 * ```
 * val users: List<User> = jsonDeserializer.deserializeList(data, User::class.java)
 * ```
 *
 * @see SingleDeserializer for deserializing individual objects
 */
interface ListDeserializer {

    /**
     * Deserialize the given string into a list of instances of the specified class.
     *
     * @param T the target element type to deserialize into
     * @param data the raw serialized input (e.g. JSON array string)
     * @param targetClass the class to deserialize each element into
     * @return a list of deserialized objects of type [T]
     */
    fun <T> deserializeList(data: String, targetClass: Class<T>): List<T>
}

internal object JsonDeserializer : SingleDeserializer, ListDeserializer {
    internal val mapper = ObjectMapper()
        .registerModule(
            KotlinModule.Builder()
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, true)
                .configure(KotlinFeature.SingletonSupport, true)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun <T> deserializeSingle(data: String, targetClass: Class<T>): T {
        return mapper.readValue(data, targetClass)
    }

    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, targetClass)
        return mapper.readValue(data, listType)
    }
}

internal object XmlDeserializer : SingleDeserializer, ListDeserializer {
    val xmlMapper: XmlMapper = XmlMapper()
        .registerModule(
            KotlinModule.Builder()
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, true)
                .configure(KotlinFeature.SingletonSupport, true)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) as XmlMapper

    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        val listType = xmlMapper.typeFactory.constructCollectionType(List::class.java, targetClass)
        return xmlMapper.readValue(data, listType)
    }

    override fun <T> deserializeSingle(data: String, targetClass: Class<T>): T {
        return xmlMapper.readValue(data, targetClass)
    }
}

/**
 * This enum is only relevant when using built-in JSON or XML deserializers.
 * Indicates which format should be used when deserializing response bodies
 * using the library’s built-in default mappers.
 *
 * @see io.github.boomkartoffel.potatocannon.result.Result.bodyAsSingle
 * @see io.github.boomkartoffel.potatocannon.result.Result.bodyAsList
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