package io.github.boomkartoffel.potatocannon.marshalling


import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.boomkartoffel.potatocannon.annotation.JsonRoot

internal object Serializer {

    val jsonMapper = jacksonObjectMapper().apply {
        findAndRegisterModules()
        disable(SerializationFeature.WRAP_ROOT_VALUE)
        registerModule(JsonAnnotationModule)
    }

    val xmlMapper: XmlMapper = XmlMapper(JacksonXmlModule().apply {
        setDefaultUseWrapper(false) // sane default; can be overridden by @XmlElementWrapper
    }).apply {
        registerKotlinModule()
        registerModule(XmlAnnotationModule)
    }

    fun <T: Any> jsonWrite(obj: T): String {
        val root = obj::class.java.getAnnotation(JsonRoot::class.java)?.value
        val w = if (root != null) jsonMapper.writer().withRootName(root) else jsonMapper.writer()
        return w.writeValueAsString(obj)
    }
}