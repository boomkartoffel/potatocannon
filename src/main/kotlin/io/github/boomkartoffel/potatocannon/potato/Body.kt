package io.github.boomkartoffel.potatocannon.potato

import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.strategy.CannonContext
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8

/**
 * Represents the body of an HTTP request used by the Potato Cannon.
 *
 * Implementations model different payload forms (text vs. binary).
 *
 * @since 0.1.0
 */
sealed interface PotatoBody

/**
 * A concrete, fully defined request body that can be sent as part of an HTTP request.
 *
 * This is either a [TextPotatoBody] or a [BinaryPotatoBody].
 *
 * @since 0.1.0
 */
sealed interface ConcretePotatoBody : PotatoBody {
    /**
     * Returns the body content as a raw byte array.
     *
     * - For [TextPotatoBody], the [String] is encoded using its configured [charset].
     * - For [BinaryPotatoBody], the underlying bytes are returned as-is.
     *
     * @return the binary representation of this body.
     */
    fun getContentAsBytes(): ByteArray =
        when (this) {
            is TextPotatoBody -> content.toByteArray(charset)
            is BinaryPotatoBody -> content
        }

    /**
     * Returns the body content as a [String].
     *
     * - For [TextPotatoBody], the original text is returned unchanged.
     * - For [BinaryPotatoBody], bytes are decoded as UTF-8 by default (per Kotlin stdlib).
     *
     * **Note:** If the binary content is not valid UTF-8, decoding may insert replacement
     * characters (�). Prefer [getContentAsBytes] for arbitrary/binary payloads.
     *
     * @return the textual representation of this body.
     */
    fun getContentAsString(): String =
        when (this) {
            is TextPotatoBody -> content
            is BinaryPotatoBody -> content.decodeToString()
        }

    /**
     * Returns the number of bytes that would be sent over the wire for this body.
     *
     * - For [TextPotatoBody], this is the size of the encoded bytes using the configured [charset]. It is **not** the number of characters / length of the string.
     * - For [BinaryPotatoBody], this is the size of the underlying byte array.
     *
     * This value is suitable for HTTP `Content-Length`.
     *
     * @return the length of the body in bytes.
     */
    fun getContentSize(): Int =
        when (this) {
            is TextPotatoBody -> content.toByteArray(charset).size
            is BinaryPotatoBody -> content.size
        }
}

/**
 * Plain-text request body; sent as-is.
 *
 * @property content Raw textual payload.
 * @property charset Charset used to encode [content] (defaults to UTF-8).
 * @property includeCharset Whether a `charset=...` parameter should be added to the `Content-Type` header
 *                          by whichever component builds headers (typically for `text/...` types).
 *                          Defaults to `false`.
 *                          If `true`, the charset is appended to the `Content-Type` header, e.g.: `Content-Type: text/plain; charset=UTF-8`.
 *                          If `true` AND the `Content-Type` header is missing, the cannon will throw an error.
 * @since 0.1.0
 */
class TextPotatoBody @JvmOverloads constructor(
    val content: String,
    val charset: Charset = UTF_8,
    val includeCharset: Boolean = false
) : ConcretePotatoBody {

    /**
     * Constructor to set only the include-charset flag while keeping UTF-8.
     *
     * Example:
     * ```
     * val body = TextBody("hello", true); // UTF-8, and add ; charset=UTF-8 to Content-Type
     * ```
     */
    constructor(content: String, includeCharset: Boolean) : this(
        content = content,
        charset = UTF_8,
        includeCharset = includeCharset
    )
}

/**
 * Binary request body.
 *
 * @property content Raw bytes to send as the request payload.
 * @since 0.1.0
 */
class BinaryPotatoBody(val content: ByteArray) : ConcretePotatoBody

/**
 * Builds the request body **at send time** from the current [CannonContext].
 *
 * Use this when the payload depends on values captured from previous requests
 * (e.g., IDs, tokens). The function must return a **concrete** body:
 * either a [TextPotatoBody] or a [BinaryPotatoBody].
 *
 * ### Resolution timing
 * - Evaluated during request preparation (just before sending), using the latest
 *   state of the shared [CannonContext].
 * - Not evaluated for TRACE requests (bodies are forbidden for TRACE).
 *
 * ### Examples
 * ```kotlin
 * // JSON body using values from the context
 * BodyFromContext { ctx ->
 *     val id = ctx.get<String>("the-key")
 *     TextBody("""{"id":"$id"}""", includeCharset = true, charset = Charsets.UTF_8)
 * }
 *
 * // Binary body
 * BodyFromContext { ctx ->
 *     BinaryBody(ctx.get<ByteArray>("payload"))
 * }
 * ```
 *
 * @throws RequestPreparationException if the function throws.
 */
class BodyFromContext(val content: (CannonContext) -> ConcretePotatoBody) : PotatoBody