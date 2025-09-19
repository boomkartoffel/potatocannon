package io.github.boomkartoffel.potatocannon.strategy

/**
 * Defines the HTTP protocol version to be used when firing requests with
 * the Potato Cannon.
 *
 * By default, [NEGOTIATE] is selected. The library will attempt to use
 * HTTP/2 for communication; however, if the connection or server does not
 * support HTTP/2, it will gracefully fall back to [HTTP_1_1].
 *
 * @since 0.1.0
 */
enum class HttpProtocolVersion : CannonSetting, PotatoSetting {

    /**
     * Force HTTP/1.1 for requests.
     *
     * Use this to enforce compatibility with servers or intermediaries that
     * do not support HTTP/2, or when you explicitly want classic HTTP/1.1
     * semantics. HTTP/2 will not be attempted.
     */
    HTTP_1_1,

    /**
     * Force HTTP/2 for requests.
     *
     * **No fallback:** if HTTP/2 cannot be negotiated (e.g., the peer does
     * not advertise `h2` via ALPN), the request will fail rather than
     * downgrade to HTTP/1.1.
     */
    HTTP_2,

    /**
     * Negotiate the protocol version with the server (default).
     *
     * The client will prefer HTTP/2 when supported by the peer, otherwise it
     * may use HTTP/1.1. Choose this when you want the most compatible behavior
     * without enforcing a specific version.
     */
    NEGOTIATE
}


/**
 * Normalized protocol family for branching and logging across HTTP versions.
 *
 * This enum intentionally collapses variants of the same major HTTP version
 * (e.g., `HTTP/1.0` and `HTTP/1.1`) into distinct families so your code can
 * make simple decisions without parsing raw protocol tokens.
 *
 * It represents the **negotiated** protocol family (what actually happened on
 * the wire), not the requested policy.
 */
enum class ProtocolFamily {

    /** HTTP/1.0 family (rare today; no persistent connections by default). */
    HTTP_1_0,

    /** HTTP/1.1 family (classic TCP-based HTTP with persistent connections). */
    HTTP_1_1,

    /** HTTP/2 family (multiplexed streams over a single connection; ALPN token usually `h2`). */
    HTTP_2,

    /** HTTP/3 family (QUIC/UDP-based; ALPN token usually `h3`). */
    HTTP_3,

    /**
     * Any other or unrecognized protocol.
     *
     * Use this when the negotiated protocol is not a standard HTTP token
     * (or a newer version that is not yet classified here). Prefer checking
     * the raw [NegotiatedProtocol.token] when you land here.
     */
    OTHER
}

/**
 * Immutable value describing the **exact** protocol negotiated for a single exchange,
 * plus a normalized [family] for easy branching.
 *
 * @property token the raw protocol identifier as reported by the HTTP library
 * (for example, `"HTTP/1.1"`, `"HTTP/2.0"`, `"HTTP/3"`, or another token).
 * @property major the parsed major version number when available (e.g., `1`, `2`, `3`);
 * may be `null` for non-HTTP or unparseable tokens.
 * @property minor the parsed minor version number when available (e.g., `0`, `1`);
 * may be `null` when not applicable.
 * @property family a normalized classification of the protocol suitable for
 * switch/when logic; falls back to [ProtocolFamily.OTHER] for unrecognized tokens.
 */
class NegotiatedProtocol internal constructor(
    val token: String,
    val major: Int?,
    val minor: Int?,
    val family: ProtocolFamily
)