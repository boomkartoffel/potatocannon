package io.github.boomkartoffel.potatocannon.marshalling

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import io.github.boomkartoffel.potatocannon.annotation.*

internal class JsonIntrospector : JacksonAnnotationIntrospector() {

    // ---- Root name (no-config override in 2.14.x) ----
    override fun findRootName(ac: AnnotatedClass): PropertyName? {
        val ann = ac.rawType.getAnnotation(JsonRoot::class.java) ?: return null
        return PropertyName(ann.value)
    }

    // ---- Per-property name (no-config overrides in 2.14.x) ----
    override fun findNameForSerialization(a: Annotated): PropertyName? {
        a.getAnnotation(JsonName::class.java)?.let { return PropertyName(it.value) }
        // If we see our JSON-related annotations, ask Jackson to use default name
        return super.findNameForSerialization(a)
    }

    override fun findNameForDeserialization(a: Annotated): PropertyName? {
        a.getAnnotation(JsonName::class.java)?.let { return PropertyName(it.value) }
        return super.findNameForDeserialization(a) ?: if (hasAlias(a)) PropertyName.USE_DEFAULT else null
    }

    // ---- Aliases (used for deserialization) ----
    override fun findPropertyAliases(a: Annotated): List<PropertyName>? {
        val ann = a.getAnnotation(JsonAliases::class.java) ?: return super.findPropertyAliases(a)
        return ann.value.map { PropertyName(it) }
    }

    override fun hasIgnoreMarker(m: AnnotatedMember): Boolean {
        findAnnotationByReflection(m, JsonIgnore::class.java)?.let { return true }
        return super.hasIgnoreMarker(m)
    }

    override fun findSerializationPropertyOrder(ac: AnnotatedClass): Array<String>? {
        val ann = ac.rawType.getAnnotation(JsonPropertyOrder::class.java)
            ?: return super.findSerializationPropertyOrder(ac)
        return ann.value.toList().toTypedArray()
    }

    private fun hasAlias(a: Annotated): Boolean =
        a.getAnnotation(JsonAliases::class.java) != null

    override fun findPropertyInclusion(a: Annotated): JsonInclude.Value? {
        val base = super.findPropertyInclusion(a)
        val ann = a.getAnnotation(JsonOmitNull::class.java) ?: return base

        // Preserve any existing settings (e.g., content inclusion), but override VALUE inclusion:
        //  - value=true  → omit nulls      → NON_NULL
        //  - value=false → include nulls   → ALWAYS (overrides any class-level NON_NULL)
        val start = base ?: JsonInclude.Value.empty()
        return if (ann.value) {
            start.withValueInclusion(JsonInclude.Include.NON_NULL)
        } else {
            start.withValueInclusion(JsonInclude.Include.ALWAYS)
        }
    }
}

internal object JsonAnnotationModule : Module() {
    override fun version(): Version = Version.unknownVersion()
    override fun getModuleName(): String = "JsonAnnotationModule"
    override fun setupModule(ctx: SetupContext) {
        ctx.insertAnnotationIntrospector(JsonIntrospector())
    }
}