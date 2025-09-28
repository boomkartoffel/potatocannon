package io.github.boomkartoffel.potatocannon.marshalling

/**
 * Wire format used by built-in codecs.
 * Applies to both request serialization and response deserialization.
 * @since 0.1.0
 */
enum class WireFormat {
    /**
     * Built-in JSON deserialization using a default Jackson ObjectMapper.
     */
    JSON,

    /**
     * Built-in XML deserialization using a default Jackson XmlMapper.
     */
    XML,
}