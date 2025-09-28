package io.github.boomkartoffel.potatocannon.annotation

// Root element name for a class
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlRoot(val value: String)

// Rename an element for a field or constructor param
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlElement(val value: String)

// Mark a field as an XML attribute (optionally rename)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlAttribute(val value: String = "")

// Control wrapper element for lists
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlElementWrapper(
    val name: String = "",          // wrapper element name if useWrapping=true
    val useWrapping: Boolean = true // false -> <Item/><Item/> directly under parent
)

// Control wrapper element for lists
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlUnwrap()

// Control wrapper element for lists
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlWrapperElement(val value : String)

// Mark a field as text content of the enclosing element
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class XmlText