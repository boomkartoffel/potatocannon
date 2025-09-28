package io.github.boomkartoffel.potatocannon.marshalling

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.dataformat.xml.JacksonXmlAnnotationIntrospector
import io.github.boomkartoffel.potatocannon.annotation.*

internal class XmlIntrospector : JacksonXmlAnnotationIntrospector() {

    override fun findRootName(ac: AnnotatedClass): PropertyName? {
        val ann = ac.rawType.getAnnotation(XmlRoot::class.java) ?: return null
        return PropertyName(ann.value)
    }

    override fun findNameForSerialization(a: Annotated): PropertyName? {
        a.getAnnotation(XmlElement::class.java)?.let { if (it.value.isNotEmpty()) return PropertyName(it.value) }
        a.getAnnotation(XmlAttribute::class.java)?.let { if (it.value.isNotEmpty()) return PropertyName(it.value) }
        return super.findNameForSerialization(a)
            ?: if (hasAnyXmlAnnotation(a)) PropertyName.USE_DEFAULT else null
    }

    override fun findNameForDeserialization(a: Annotated): PropertyName? {
        findAnnotationByReflection(
            a,
            XmlElement::class.java
        )?.let { if (it.value.isNotEmpty()) return PropertyName(it.value) }
        findAnnotationByReflection(
            a,
            XmlAttribute::class.java
        )?.let { if (it.value.isNotEmpty()) return PropertyName(it.value) }
//        findAnnotationByReflection(
//            a,
//            XmlElementWrapper::class.java
//        )?.let { if (it.name.isNotEmpty()) return PropertyName(it.name) }
        return super.findNameForDeserialization(a)
            ?: if (hasAnyXmlAnnotation(a)) PropertyName.USE_DEFAULT else null
    }

    //    // Element wrapper for collections/arrays
//    override fun findWrapperName(a: Annotated): PropertyName? {
//        if (a.type.toString() == "[collection type; class java.util.List, contains [simple type, class io.github.boomkartoffel.potatocannon.PotatoCannonTest\$XmlModels\$Item]]") {
//            return PropertyName("items")
//        }
//        val w = findAnnotationByReflection(a, XmlElementWrapper::class.java)
//            ?: return super.findWrapperName(a)
//        println(w.name)
//        return when {
//            !w.useWrapping -> PropertyName.NO_NAME
//            w.name.isNotEmpty() -> PropertyName(w.name)
//            else -> PropertyName.USE_DEFAULT
//        }
//    }
    override fun findWrapperName(a: Annotated): PropertyName? {
        findAnnotationByReflection(a, XmlUnwrap::class.java)?.let { return PropertyName.NO_NAME }
        findAnnotationByReflection(
            a,
            XmlWrapperElement::class.java
        )?.let { if (it.value.isNotEmpty()) return PropertyName(it.value) }
        return PropertyName.USE_DEFAULT
//    return when {
//            !w.useWrapping -> PropertyName.NO_NAME
//            w.name.isNotEmpty() -> PropertyName(w.name)
//            else -> PropertyName.USE_DEFAULT
//        }
    }

    override fun isOutputAsAttribute(config: MapperConfig<*>, ann: Annotated): Boolean {
        return findAnnotationByReflection(ann, XmlAttribute::class.java) != null
                || super.isOutputAsAttribute(config, ann) == true
    }

    override fun isOutputAsText(config: MapperConfig<*>, ann: Annotated): Boolean {
        return findAnnotationByReflection(ann, XmlText::class.java) != null
                || super.isOutputAsText(config, ann) == true
    }

}

private fun hasAnyXmlAnnotation(a: Annotated): Boolean =
    a.getAnnotation(XmlElement::class.java) != null ||
            a.getAnnotation(XmlAttribute::class.java) != null ||
            a.getAnnotation(XmlText::class.java) != null ||
            a.getAnnotation(XmlElementWrapper::class.java) != null


internal object XmlAnnotationModule : Module() {
    override fun version(): Version = Version.unknownVersion()
    override fun getModuleName(): String = "XmlAnnotationModule"

    override fun setupModule(ctx: SetupContext) {
        ctx.insertAnnotationIntrospector(XmlIntrospector())
    }
}
