package io.github.boomkartoffel.potatocannon.marshalling

import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter
import java.util.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor


fun debugAnn(a: Annotated, owner: Class<*>) {
    println("== DUMP ${owner.name} ==")
    println("== DUMP ${a.name} ==")
    // Kotlin properties
    owner.kotlin.declaredMemberProperties.forEach { kp ->
        println("KProp ${kp.name}: ${kp.annotations.map { it.annotationClass.qualifiedName }}")
    }
    // Java fields
    owner.declaredFields.forEach { f ->
        println("Field ${f.name}: ${f.declaredAnnotations.map { it.annotationClass.qualifiedName }}")
    }
    // Java getters
    owner.declaredMethods
        .filter { it.name.startsWith("get") || it.name.startsWith("is") }
        .forEach { m ->
            println("Getter ${m.name}: ${m.declaredAnnotations.map { it.annotationClass.qualifiedName }}")
        }
    // Primary ctor params (Kotlin)
    runCatching {
        owner.kotlin.primaryConstructor?.parameters?.forEachIndexed { i, k ->
            println("CtorParam[$i] name=${k.name} anns=${k.annotations.map { it.annotationClass.qualifiedName }}")
        }
    }
}

/**
 * Try to find [annClass] on a Jackson [Annotated] member, looking at:
 *  1) the accessor itself (getter/field/param)
 *  2) Java backing members (field/getter)
 *  3) Kotlin property (property annotation / javaField / javaGetter)
 */
internal fun <A : Annotation> findAnnotationByReflection(
    a: Annotated,
    annClass: Class<A>
): A? {

    val directAnnotation = a.getAnnotation(annClass)
    if (directAnnotation != null) return directAnnotation

    val member = (a as? AnnotatedMember)?.member
    val owner: Class<*> = member?.declaringClass ?: return null

    val propName: String? = run {
        val raw = a.name
        when {
            raw == null && a is AnnotatedParameter ->
                runCatching { owner.kotlin.primaryConstructor?.parameters?.getOrNull(a.index)?.name }.getOrNull()

            raw != null && raw.startsWith("get") && raw.length > 3 ->
                raw.substring(3).replaceFirstChar { it.lowercaseChar() }

            raw != null && raw.startsWith("is") && raw.length > 2 ->
                raw.substring(2).replaceFirstChar { it.lowercaseChar() }

            raw != null && raw.startsWith("set") && raw.length > 3 ->
                raw.substring(3).replaceFirstChar { it.lowercaseChar() }

            else -> raw
        }
    }
    if (propName.isNullOrEmpty()) return null

    // 1) Kotlin properties
    owner.kotlin.declaredMemberProperties
        .firstOrNull { it.name == propName }
        ?.annotations
        ?.firstOrNull { annClass.isInstance(it) }
        ?.let { return annClass.cast(it) }

    // 2) Java fields
    owner.declaredFields
        .firstOrNull { it.name == propName }
        ?.annotations
        ?.firstOrNull { annClass.isInstance(it) }
        ?.let { return annClass.cast(it) }

    // NEW: record component (safe on 11; no-ops unless running on 16+)
    findRecordComponentAnnotation(owner, propName!!, annClass)?.let { return annClass.cast(it) }


    // 3) Java getters (getX / isX)
    val cap = propName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    owner.declaredMethods
        .filter { it.name.startsWith("get") || it.name.startsWith("is") }
        .firstOrNull { it.name == "get$cap" || it.name == "is$cap" }
        ?.annotations
        ?.firstOrNull { annClass.isInstance(it) }
        ?.let { return annClass.cast(it) }

    // 4) Kotlin primary ctor params (by name)
    owner.kotlin.primaryConstructor?.parameters
        ?.firstOrNull { it.name == propName }
        ?.annotations
        ?.firstOrNull { annClass.isInstance(it) }
        ?.let { return annClass.cast(it) }

    // 4b) Java ctor param (by index) – covers Java classes/records when Jackson gives AnnotatedParameter
    (a as? AnnotatedParameter)?.let { p ->
        owner.declaredConstructors
            .firstOrNull { it.parameterCount > p.index }
            ?.parameterAnnotations?.getOrNull(p.index)
            ?.firstOrNull { annClass.isInstance(it) }
            ?.let { return annClass.cast(it) }
    }

    return null

}

// Safe on JDK 11; uses reflection if records exist (16+), otherwise returns null.
private fun <A : Annotation> findRecordComponentAnnotation(
    owner: Class<*>,
    propName: String,
    annClass: Class<A>
): A? {
    return try {
        val classClass = Class::class.java

        // Class#isRecord(): Boolean
        val isRecord = classClass.getMethod("isRecord").invoke(owner) as Boolean
        if (!isRecord) return null

        // Class#getRecordComponents(): RecordComponent[]
        val comps = classClass.getMethod("getRecordComponents").invoke(owner) as Array<*>

        // RecordComponent#getName(): String
        val comp = comps.firstOrNull { c ->
            val name = c!!.javaClass.getMethod("getName").invoke(c) as String
            name == propName
        } ?: return null

        // RecordComponent#getAnnotations(): Annotation[]
        val anns = comp.javaClass.getMethod("getAnnotations").invoke(comp) as Array<Annotation>
        anns.firstOrNull { annClass.isInstance(it) }?.let { annClass.cast(it) }
    } catch (_: Exception) {
        null
    }
}