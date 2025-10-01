package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.strategy.*


sealed interface FireablePotato

/**
 * Represents an HTTP request definition to be fired by the [Cannon].
 *
 * A `Potato` encapsulates everything needed to define an HTTP call, including method, path,
 * optional body, and optional settings such as headers, query parameters, and expectations.
 *
 * This class is immutable; use `with*` builders or factory helpers to derive new instances.
 *
 * ## Subclassing
 * This type is `open` for internal reasons, but **external subclassing is not supported**.
 * The firing pipeline is designed to handle only the base [Potato] instances and the deferred
 * variant provided by [PotatoFromContext]; other user-defined subclasses may be **ignored** or
 * behave unpredictably. Future versions may further restrict inheritance (e.g., by sealing the
 * hierarchy or making constructors internal).
 *
 * Prefer composition via [PotatoSetting] and deferred construction via
 * [PotatoFromContext.single] / [PotatoFromContext.many] instead of inheritance.
 *
 * @property method The HTTP method to use (e.g., GET, POST, PUT, DELETE).
 * @property path The relative path of the request (e.g., "/users").
 * @property body Optional request body, either textual or binary.
 * @property settings Optional list of [PotatoSetting] items like [HeaderStrategy], [QueryParam],
 *   [LogCommentary], or [Expectation] for the request.
 * @see PotatoFromContext
 * @see PotatoFromContext.single
 * @see PotatoFromContext.many
 * @since 0.1.0
 */
class Potato(
    val method: HttpMethod,
    val path: String,
    val body: PotatoBody?,
    val settings: List<PotatoSetting>,
): FireablePotato {
    constructor(
        method: HttpMethod,
        path: String,
        vararg settings: PotatoSetting
    ) : this(method, path, null, settings.toList())

    constructor(
        method: HttpMethod,
        path: String,
        settings: List<PotatoSetting>
    ) : this(method, path, null, settings)

    constructor(
        method: HttpMethod,
        path: String,
        body: PotatoBody,
        vararg settings: PotatoSetting
    ) : this(method, path, body, settings.toList())

    /**
     * Returns a copy of this [Potato] with a different HTTP method.
     *
     * Keeps the current path, body, and settings unchanged.
     *
     * @param newMethod The HTTP method to set (e.g., GET, POST).
     * @return A new [Potato] with [newMethod].
     * @since 0.1.0
     */
    fun withMethod(newMethod: HttpMethod): Potato =
        Potato(newMethod, path, body, settings)

    /**
     * Returns a copy of this [Potato] with a different request path.
     *
     * Keeps the current method, body, and settings unchanged.
     *
     * @param newPath The request path (e.g., "/health").
     * @return A new [Potato] with [newPath].
     * @since 0.1.0
     */
    fun withPath(newPath: String): Potato =
        Potato(method, newPath, body, settings)

    /**
     * Adds an [Expectation] to this [Potato]’s settings.
     *
     * This is a convenience for [addSettings]; the expectation is appended to the
     * end of the settings list, preserving existing entries.
     *
     * @param expectation The expectation to add.
     * @return A new [Potato] with the expectation appended.
     * @since 0.1.0
     */
    fun addExpectation(expectation: Expectation): Potato =
        this.addSettings(expectation)

    /**
     * Returns a copy of this [Potato] with a different request body.
     *
     * Passing `null` clears the body. Method, path, and settings remain unchanged.
     *
     * @param newBody The new body to use, or `null` to remove it.
     * @return A new [Potato] with [newBody].
     * @since 0.1.0
     */
    fun withBody(newBody: PotatoBody?): Potato =
        Potato(method, path, newBody, settings)

    /**
     * Replaces the entire settings list for this [Potato].
     *
     * Use this to set an explicit settings snapshot. If you want to **append**
     * additional options to the current list instead, prefer [addSettings].
     *
     * @param newSettings The complete [PotatoSetting] list to use.
     * @return A new [Potato] with [newSettings].
     * @since 0.1.0
     */
    fun withSettings(newSettings: List<PotatoSetting>): Potato =
        Potato(method, path, body, newSettings)

    /**
     * Vararg convenience for replacing the entire settings list for the [Potato].
     *
     * If you want to **append** items to the existing settings, use
     * [addSettings] instead.
     *
     * @param newSettings The complete [PotatoSetting] to set.
     * @return A new [Potato] with [newSettings].
     * @since 0.1.0
     */
    fun withSettings(vararg newSettings: PotatoSetting): Potato = this.withSettings(newSettings.toList())

    /**
     * Appends one or more settings entries to this [Potato].
     *
     * Existing settings entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedSettings The [PotatoSetting] entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addSettings(vararg addedSettings: PotatoSetting): Potato =
        this.withSettings(settings + addedSettings)

    /**
     * Appends one or more settings entries to this [Potato].
     *
     * Existing settings entries are preserved and the new ones are appended
     * **in the given order**.
     *
     * @param addedSettings The [PotatoSetting] entries to append.
     * @return A new [Potato] with the entries appended.
     * @since 0.1.0
     */
    fun addSettings(addedSettings: List<PotatoSetting>): Potato =
        this.withSettings(settings + addedSettings)

    companion object {
        private fun build(
            method: HttpMethod,
            path: String,
            body: PotatoBody?,
            settings: List<PotatoSetting>
        ) = Potato(method, path, body, settings)

        // ---- POST ----
        @JvmStatic
        fun post(path: String, vararg settings: PotatoSetting) =
            build(HttpMethod.POST, path, null, settings.toList())

        @JvmStatic
        fun post(path: String, settings: List<PotatoSetting>) =
            build(HttpMethod.POST, path, null, settings)

        @JvmStatic
        fun post(path: String, body: PotatoBody, vararg settings: PotatoSetting) =
            build(HttpMethod.POST, path, body, settings.toList())

        @JvmStatic
        fun post(path: String, body: PotatoBody, settings: List<PotatoSetting>) =
            build(HttpMethod.POST, path, body, settings)

        // ---- GET ----
        @JvmStatic
        fun get(path: String, vararg settings: PotatoSetting) =
            build(HttpMethod.GET, path, null, settings.toList())

        @JvmStatic
        fun get(path: String, settings: List<PotatoSetting>) =
            build(HttpMethod.GET, path, null, settings)

        @JvmStatic
        fun get(path: String, body: PotatoBody, vararg settings: PotatoSetting) =
            build(HttpMethod.GET, path, body, settings.toList())

        @JvmStatic
        fun get(path: String, body: PotatoBody, settings: List<PotatoSetting>) =
            build(HttpMethod.GET, path, body, settings)

        // ---- PUT ----
        @JvmStatic
        fun put(path: String, vararg settings: PotatoSetting) =
            build(HttpMethod.PUT, path, null, settings.toList())

        @JvmStatic
        fun put(path: String, settings: List<PotatoSetting>) =
            build(HttpMethod.PUT, path, null, settings)

        @JvmStatic
        fun put(path: String, body: PotatoBody, vararg settings: PotatoSetting) =
            build(HttpMethod.PUT, path, body, settings.toList())

        @JvmStatic
        fun put(path: String, body: PotatoBody, settings: List<PotatoSetting>) =
            build(HttpMethod.PUT, path, body, settings)

        // ---- PATCH ----
        @JvmStatic
        fun patch(path: String, vararg settings: PotatoSetting) =
            build(HttpMethod.PATCH, path, null, settings.toList())

        @JvmStatic
        fun patch(path: String, settings: List<PotatoSetting>) =
            build(HttpMethod.PATCH, path, null, settings)

        @JvmStatic
        fun patch(path: String, body: PotatoBody, vararg settings: PotatoSetting) =
            build(HttpMethod.PATCH, path, body, settings.toList())

        @JvmStatic
        fun patch(path: String, body: PotatoBody, settings: List<PotatoSetting>) =
            build(HttpMethod.PATCH, path, body, settings)

        // ---- DELETE ----
        @JvmStatic
        fun delete(path: String, vararg settings: PotatoSetting) =
            build(HttpMethod.DELETE, path, null, settings.toList())

        @JvmStatic
        fun delete(path: String, settings: List<PotatoSetting>) =
            build(HttpMethod.DELETE, path, null, settings)

        @JvmStatic
        fun delete(path: String, body: PotatoBody, vararg settings: PotatoSetting) =
            build(HttpMethod.DELETE, path, body, settings.toList())

        @JvmStatic
        fun delete(path: String, body: PotatoBody, settings: List<PotatoSetting>) =
            build(HttpMethod.DELETE, path, body, settings)
    }
}

class PotatoFromContext private constructor(
    internal val builder: (ContextView) -> List<Potato>
) : FireablePotato {

    internal fun resolve(ctx: ContextView): List<Potato> = builder(ctx)

    companion object {
        @JvmStatic
        fun single(builder: (ContextView) -> Potato) =
            PotatoFromContext { ctx -> listOf(builder(ctx)) }

        @JvmStatic
        fun many(builder: (ContextView) -> List<Potato>) =
            PotatoFromContext { ctx -> builder(ctx) }
    }
}
